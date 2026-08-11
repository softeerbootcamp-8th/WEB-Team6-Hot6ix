package com.hot6ix.upbid.domain.auction.scheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 물품 마감 계측만 아는 객체. 분리한 이유는 {@code BidMetrics}와 같다.
 *
 * <p>마감을 두 구간으로 나눠 잰다. {@code delay}는 예정 시각부터 실행이 시작되기까지고
 * {@code duration}은 그 뒤 실행에 걸린 시간이다. 앞이 크면 스케줄러 스레드를 늘리는 게 답이고
 * 뒤가 크면 스레드를 늘려도 커넥션만 더 잡아먹으므로, 처방이 갈리는 만큼 지표도 갈라 둔다.
 *
 * <p><b>{@code duration}이 큰 것까지는 알아도 그 안 어디가 큰지는 못 가른다.</b> 그래서
 * {@code lock.wait}과 {@code lock.hold}를 함께 잰다. 나머지 두 조각인 알림과 낙찰 후보 생성은
 * 커밋 뒤 리스너에서 도는 것이라 {@code SseMetrics}와 {@code DealMetrics}가 맡는다.
 */
@Component
public class AuctionCloseMetrics {

    private static final String DURATION = "upbid.auction.close.duration";

    /** {@code reason} 태그가 붙어야 해서 Counter를 미리 만들 수 없다. 그 하나 때문에 들고 있는다. */
    private final MeterRegistry registry;

    /** 첫 마감이 일어나기 전에도 지표가 보이도록 생성자에서 미리 만든다. */
    private final Timer closeDelay;

    /**
     * 태그값마다 하나씩 미리 만들어 둔다. 매번 {@code registry.timer(...)}로 찾으면 첫 마감
     * 전까지 지표가 없어서, 측정 전에 계측이 붙었는지 확인할 수도 없고 시계열이 구간 중간에
     * 생겨 증가분도 못 구한다. 값이 둘뿐이라 미리 만들어도 부담이 없다.
     */
    private final Timer closeDurationClosed;

    private final Timer closeDurationRescheduled;

    /**
     * 마감이 물품 행 락을 잡기까지 기다린 시간. 입찰과 같은 행을 두고 줄을 서므로
     * {@code upbid.bid.lock.wait}과 나란히 놓고 봐야 누가 누구를 기다리게 하는지 갈린다.
     *
     * <p>{@link #closeDelay}와 같은 이유로 생성자에서 미리 만든다.
     */
    private final Timer lockWait;

    /**
     * 마감이 락을 잡고 있던 시간. 기다린 시간만 재면 줄이 길어서 오래 걸린 것인지 앞사람이
     * 오래 잡고 있어서인지 못 가른다. 앞쪽이면 요청을 흩어서 풀리고 뒤쪽이면 안 풀린다.
     */
    private final Timer lockHold;

    public AuctionCloseMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.closeDelay = registry.timer("upbid.auction.close.delay");
        this.closeDurationClosed = registry.timer(DURATION, "result", "closed");
        this.closeDurationRescheduled = registry.timer(DURATION, "result", "rescheduled");
        this.lockWait = registry.timer("upbid.auction.close.lock.wait");
        this.lockHold = registry.timer("upbid.auction.close.lock.hold");
    }

    /**
     * 마감이 물품 행 락을 잡는 동안 걸린 시간을 잰다. 락을 잡고 나면 보유 시간 측정을 이어서
     * 시작한다. 예외가 나도 대기 시간을 기록하는 것까지 {@code BidMetrics}와 같은 방식이다.
     */
    public <T> T recordLockWait(Supplier<T> lockedRead) {

        T locked = lockWait.record(lockedRead);

        // 예외로 끝나면 락이 없으므로 여기까지 오지 않는다.
        startLockHold();

        return locked;
    }

    /**
     * 락을 잡은 지금부터 <b>트랜잭션이 끝날 때까지</b>를 잰다. 행 락이 커밋 시점에 풀려서
     * 메서드 리턴까지만 재면 커밋에 걸린 시간이 빠지기 때문이다. 트랜잭션 밖에서 불리면 잴 수가
     * 없어 그냥 넘어간다. 자세한 이유는 {@code BidMetrics}에 적어 두었다.
     */
    private void startLockHold() {

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        long acquiredAt = System.nanoTime();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                lockHold.record(System.nanoTime() - acquiredAt, TimeUnit.NANOSECONDS);
            }
        });
    }

    /**
     * 예약한 시각보다 실제 마감이 얼마나 늦었는지 기록한다.
     *
     * <p>예약보다 일찍 실행되면 음수가 나오는데 Timer는 음수를 담지 못하므로 0으로 눕힌다.
     */
    public void recordCloseDelay(long delayMillis) {
        closeDelay.record(Math.max(delayMillis, 0L), TimeUnit.MILLISECONDS);
    }

    /**
     * 마감 한 건이 스레드를 붙잡은 시간을 잰다. 예외가 나도 기록하고 예외는 그대로 올린다.
     * 실패한 마감이 가장 오래 붙잡은 경우라 빼고 세면 p95가 실제보다 낮게 나온다.
     *
     * <p>결과를 {@code result} 태그로 가른다. Soft Close 연장이 락을 기다리는 사이에 커밋되면
     * 닫지 않고 재예약만 하는데, 그 실행까지 섞으면 실제로 닫은 마감의 p95가 흐려지기 때문이다.
     * 예외로 끝난 실행은 {@code closed}로 센다. 건수는 {@link #recordFailure}가 따로 센다.
     *
     * @param rescheduled 결과가 재예약이면 {@code true}를 주는 판정. 부르는 쪽에 Micrometer를
     *                    노출하지 않으려고 결과 타입을 모른 채 받는다
     */
    public <T> T recordCloseDuration(Supplier<T> close, Predicate<T> rescheduled) {

        long startedAt = System.nanoTime();
        T result = null;

        try {
            result = close.get();
            return result;
        } finally {
            Timer timer = result != null && rescheduled.test(result)
                    ? closeDurationRescheduled
                    : closeDurationClosed;

            timer.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 마감이 예외로 끝난 것을 센다. 로그로만 남기면 측정 프로파일이 로그를 warn으로 내려 둔 데다
     * {@code index.csv}에도 못 들어가서, 재시도를 붙일지 정할 근거가 안 된다.
     *
     * <p>{@code reason}은 예외 클래스 이름이다. 락 타임아웃은 재시도로 풀리지만 낙관적 락 실패나
     * NPE는 다시 해도 똑같이 실패해서, 이 둘을 갈라야 재시도가 의미 있는지 말할 수 있다.
     *
     * <p>첫 실패 전에는 시계열이 아예 없다. 그래서 {@code run.sh}의 계측 확인 목록에는 넣지 않고
     * 값을 뽑을 때 {@code or vector(0)}으로 받는다.
     */
    public void recordFailure(Exception e) {
        Counter.builder("upbid.auction.close.failures")
                .tag("reason", e.getClass().getSimpleName())
                .register(registry)
                .increment();
    }
}
