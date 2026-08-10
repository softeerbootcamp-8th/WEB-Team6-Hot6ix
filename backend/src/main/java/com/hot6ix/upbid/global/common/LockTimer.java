package com.hot6ix.upbid.global.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 행 락을 기다린 시간과 잡고 있던 시간을 재는 계측기. 입찰과 마감이 같은 물품 행을 두고 서로
 * 줄을 서므로 둘을 같은 방식으로 재야 나란히 놓고 볼 수 있어서 공용으로 뺐다.
 *
 * <p>Spring 빈이 아니라 {@code XxxMetrics}가 필드로 들고 쓰는 도구다. 도메인마다 지표 이름이
 * 달라야 하는데 빈으로 두면 하나밖에 못 만든다.
 */
public class LockTimer {

    /**
     * 락을 잡기까지 기다린 시간. 락 대기는 기성 지표에 없어서 직접 심어야 하고, 이게 없으면
     * "자원은 다 여유로운데 처리량이 안 오른다"의 원인이 락이라는 것을 숫자로 말할 수 없다.
     *
     * <p><b>생성자에서 미리 만든다.</b> {@code registry.timer(name)}은 처음 불릴 때 지표를
     * 등록하므로, 첫 호출을 기다리면 그 전까지 {@code /actuator/prometheus}에 이 지표가 아예
     * 없다. 그러면 측정 전에 계측이 붙었는지 확인할 수 없고, 시계열이 측정 구간 중간에 생겨서
     * 구간 증가분을 구할 때 시작값을 알 수 없다.
     */
    private final Timer wait;

    /**
     * 락을 잡고 있던 시간. 기다린 시간만 재면 줄이 길어서 오래 걸린 것인지 앞사람이 오래 잡고
     * 있어서인지 못 가른다. 앞쪽이면 요청을 흩어서 풀리고 뒤쪽이면 안 풀린다.
     *
     * <p>{@link #wait}과 같은 이유로 생성자에서 미리 만든다.
     */
    private final Timer hold;

    /**
     * @param prefix 지표 이름 앞부분. {@code upbid.bid}를 주면 {@code upbid.bid.lock.wait}와
     *               {@code upbid.bid.lock.hold}가 된다
     */
    public LockTimer(MeterRegistry registry, String prefix) {
        this.wait = registry.timer(prefix + ".lock.wait");
        this.hold = registry.timer(prefix + ".lock.hold");
    }

    /**
     * 행 락을 잡는 동안 걸린 시간을 잰다. 락을 잡고 나면 {@link #hold} 측정을 이어서 시작한다.
     *
     * <p><b>예외가 나도 대기 시간은 기록한다</b>({@code Timer.record}가 내부에서 try/finally를
     * 돈다). 그게 중요한 이유는 실패한 요청이야말로 가장 오래 기다린 요청이기 때문이다. 락
     * 획득이 {@code innodb_lock_wait_timeout}에 걸린 요청을 빼고 세면 경합이 심해질수록 p95가
     * 오히려 낮게 나오는데, 경합이 극단으로 가는 지점이 정확히 우리가 보려는 곳이다.
     */
    public <T> T recordWait(Supplier<T> lockedRead) {

        T locked = wait.record(lockedRead);

        // 예외로 끝나면 락이 없으므로 여기까지 오지 않는다.
        startHold();

        return locked;
    }

    /**
     * 락을 잡은 지금부터 <b>트랜잭션이 끝날 때까지</b>를 잰다.
     *
     * <p>행 락은 커밋 시점에 풀리는데 커밋은 {@code @Transactional} 프록시가 메서드 밖에서
     * 한다. 그래서 "락 획득 ~ 메서드 리턴"만 재면 커밋에 걸린 시간이 빠진다. 트랜잭션 동기화를
     * 걸어 {@code afterCompletion}에서 기록하면 실제로 락이 풀리는 시점까지 들어온다.
     *
     * <p>트랜잭션 밖에서 불리면 잴 수가 없어 그냥 넘어간다. 그런 호출은 락도 안 잡는다.
     */
    private void startHold() {

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        long acquiredAt = System.nanoTime();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                hold.record(System.nanoTime() - acquiredAt, TimeUnit.NANOSECONDS);
            }
        });
    }
}
