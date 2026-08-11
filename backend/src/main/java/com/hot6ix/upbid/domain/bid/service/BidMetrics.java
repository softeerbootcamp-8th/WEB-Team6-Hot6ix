package com.hot6ix.upbid.domain.bid.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 입찰 계측만 아는 객체.
 *
 * <p>{@code BidService}가 Micrometer를 직접 들고 있으면 도메인 로직 사이에 계측 코드가 섞여
 * 읽기 어려워지고, 생성자에 인프라 타입이 하나 더 붙는다. 그걸 여기로 옮겨서 서비스는
 * "무엇을 잰다"만 알고 "어떻게 재는지"는 모르게 한다.
 *
 * <p><b>입찰 한 건을 네 구간으로 나눈다.</b> {@code --vus}를 40, 80, 160으로 올렸을 때
 * 어느 구간의 값이 커지는지를 본다. {@code beforeLock}이나 {@code commit}이 커지면 그 안에서
 * 도는 쿼리를 줄여 값을 낮출 수 있다. {@code lockWait}만 커지면 같은 {@code auction_item_id}에
 * 대한 입찰이 한 번에 하나씩만 처리되기 때문이므로, 쿼리를 줄여도 그 값은 안 내려간다.
 *
 * <pre>
 *   place() 진입
 *      │
 *      │  {@link #beforeLock}  커넥션 획득 + 락 없이 되는 SELECT 3개
 *      │                       (users, 판매자, 참여 여부)
 *      ▼
 *   findByIdForUpdate 호출
 *      │
 *      │  {@link #lockWait}    앞 요청이 물품 행 락을 놓기를 기다린 시간
 *      ▼
 *   락 획득
 *      │
 *      │  {@link #lockHoldAccepted} / {@link #lockHoldRejected}
 *      │                       락을 잡고 있던 전체 시간 (아래 commit 포함)
 *      │
 *      │    검증 → (거절이면 여기서 예외)
 *      │    insert bids, 이벤트 payload 만들며 products·auction_rooms 조회
 *      │      ┌─────────────────────────────────────────
 *      │      │ {@link #commit}  update auction_items flush + COMMIT
 *      ▼      └─────────────────────────────────────────
 *   락 해제
 * </pre>
 *
 * <p>커넥션 획득만 따로 재지 않는다. HikariCP가 {@code hikaricp.connections.acquire}로 이미
 * 내보내고 있어서, {@code beforeLock}에서 그 값을 빼면 SELECT 3개에 걸린 시간이 나온다.
 */
@Component
public class BidMetrics {

    /**
     * 요청이 {@code place()}에 들어와서 물품 행 락을 잡으러 가기까지.
     *
     * <p>락을 잡기 전에 도는 SELECT 3개는 지금까지 어느 타이머에도 안 들어 있었다. 그래서
     * 커넥션을 받은 뒤 {@code findByIdForUpdate}까지 몇 ms가 걸리는지 알 수 없었다.
     * 이 값이 크면 커넥션을 잡고 있는 시간의 상당 부분이 락 앞 SELECT 3개다.
     */
    private final Timer beforeLock;

    /**
     * 물품 행 락을 잡기까지 기다린 시간. 락 대기는 기성 지표에 없어서 직접 심어야 하고,
     * 이게 없으면 "자원은 다 여유로운데 처리량이 안 오른다"의 원인이 락이라는 것을 숫자로
     * 말할 수 없다.
     *
     * <p><b>생성자에서 미리 만든다.</b> {@code registry.timer(name)}은 처음 불릴 때 지표를
     * 등록하므로, 첫 입찰을 기다리면 그 전까지 {@code /actuator/prometheus}에 이 지표가 아예
     * 없다. 그러면 측정 전에 계측이 붙었는지 확인할 수 없고, 시계열이 측정 구간 중간에
     * 생겨서 구간 증가분을 구할 때 시작값을 알 수 없다.
     */
    private final Timer lockWait;

    /**
     * 접수된 입찰이 락을 잡고 있던 시간. 기다린 시간만 재면 줄이 길어서 오래 걸린 것인지
     * 앞사람이 오래 잡고 있어서인지 못 가른다. 앞쪽이면 요청을 흩어서 풀리고 뒤쪽이면 안 풀린다.
     *
     * <p><b>접수와 거절을 갈라 두는 이유.</b> 물품 1개에 부하를 몰면 요청의 94%가
     * {@code BID_AMOUNT_TOO_LOW}로 거절되는데(실측: 687건/s 중 647건), 거절 경로는 락 안에서
     * 검증만 하고 롤백해서 짧다. 둘을 한 타이머에 넣으면 평균 1.01ms가 나오는데, 그 값은
     * 거절 경로 94%가 만든 것이라 접수 경로가 락을 몇 ms 잡는지는 알 수 없다.
     * 같은 조건에서 물품을 20개로 늘려 접수 비율이 41%로 오르자 이 값이 p95 1ms에서 5ms로
     * 올랐다. 두 경로의 시간이 다르다.
     *
     * <p>{@link #lockWait}과 같은 이유로 생성자에서 미리 만든다.
     */
    private final Timer lockHoldAccepted;

    /** 거절된 입찰이 락을 잡고 있던 시간. 가르는 이유는 {@link #lockHoldAccepted} 참고. */
    private final Timer lockHoldRejected;

    /**
     * 커밋에 걸린 시간. 접수된 입찰에만 찍힌다.
     *
     * <p>락 보유 시간을 줄이려 할 때 "쿼리를 빼서 줄일 수 있는 부분"과 "커밋이라 못 줄이는
     * 부분"을 갈라야 한다. 락 보유가 대부분 커밋이면 쿼리를 아무리 빼도 안 짧아진다.
     *
     * <p>{@code saveAndFlush}가 insert를 이미 내보냈으므로 여기 남는 것은
     * {@code update auction_items} flush와 실제 COMMIT이다.
     */
    private final Timer commit;

    public BidMetrics(MeterRegistry registry) {
        this.beforeLock = registry.timer("upbid.bid.before.lock");
        this.lockWait = registry.timer("upbid.bid.lock.wait");
        // 태그로 가른다. run.sh 가 쓰는 histogram_quantile 은 sum by (le) 라 태그를 합쳐서
        // 계산하므로, upbid_bid_lock_hold 전체 p95 는 태그를 붙이기 전과 같은 값이 나온다.
        this.lockHoldAccepted = registry.timer("upbid.bid.lock.hold", "result", "accepted");
        this.lockHoldRejected = registry.timer("upbid.bid.lock.hold", "result", "rejected");
        this.commit = registry.timer("upbid.bid.commit");
    }

    /**
     * 락을 잡으러 가기 직전에 부른다.
     *
     * @param enteredAt {@code place()} 진입 시점의 {@code System.nanoTime()}
     */
    public void recordBeforeLock(long enteredAt) {
        beforeLock.record(System.nanoTime() - enteredAt, TimeUnit.NANOSECONDS);
    }

    /**
     * 물품 행 락을 잡는 동안 걸린 시간을 잰다. 락을 잡고 나면 락 보유 측정을 이어서 시작한다.
     *
     * <p><b>예외가 나도 대기 시간은 기록한다</b>({@code Timer.record}가 내부에서 try/finally를
     * 돈다). 그게 중요한 이유는 실패한 요청이야말로 가장 오래 기다린 요청이기 때문이다. 락
     * 획득이 {@code innodb_lock_wait_timeout}에 걸린 요청을 빼고 세면 경합이 심해질수록 p95가
     * 오히려 낮게 나오는데, 경합이 극단으로 가는 지점이 정확히 우리가 보려는 곳이다.
     */
    public <T> T recordLockWait(Supplier<T> lockedRead) {

        T locked = lockWait.record(lockedRead);

        // 예외로 끝나면 락이 없으므로 여기까지 오지 않는다.
        startLockHold();

        return locked;
    }

    /**
     * 락을 잡은 지금부터 <b>트랜잭션이 끝날 때까지</b>를 잰다.
     *
     * <p>행 락은 커밋 시점에 풀리는데 커밋은 {@code @Transactional} 프록시가 메서드 밖에서
     * 한다. 그래서 "락 획득 ~ 메서드 리턴"만 재면 커밋에 걸린 시간이 빠진다. 트랜잭션 동기화를
     * 걸어 {@code afterCompletion}에서 기록하면 실제로 락이 풀리는 시점까지 들어온다.
     *
     * <p><b>접수와 거절은 트랜잭션 상태로 가른다.</b> 입찰 거절은 전부 {@code ApplicationException}
     * 이고 그것이 {@code RuntimeException}이라 트랜잭션이 롤백된다. 그래서 커밋으로 끝났으면
     * 접수, 롤백으로 끝났으면 거절이다. 서비스에서 결과를 따로 넘겨줄 필요가 없다.
     *
     * <p>{@code beforeCommit}은 롤백 경로에서는 안 불린다. 그래서 커밋 시간은 접수 경로에만
     * 찍힌다.
     *
     * <p>트랜잭션 밖에서 불리면 잴 수가 없어 그냥 넘어간다. 그런 호출은 락도 안 잡는다.
     */
    private void startLockHold() {

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        long acquiredAt = System.nanoTime();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            private long commitStartedAt;

            @Override
            public void beforeCommit(boolean readOnly) {
                commitStartedAt = System.nanoTime();
            }

            @Override
            public void afterCompletion(int status) {

                long completedAt = System.nanoTime();

                if (status != STATUS_COMMITTED) {
                    lockHoldRejected.record(completedAt - acquiredAt, TimeUnit.NANOSECONDS);
                    return;
                }

                lockHoldAccepted.record(completedAt - acquiredAt, TimeUnit.NANOSECONDS);
                commit.record(completedAt - commitStartedAt, TimeUnit.NANOSECONDS);
            }
        });
    }
}
