package com.hot6ix.upbid.domain.bid.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 입찰 계측만 아는 객체.
 *
 * <p>{@code BidService}가 Micrometer를 직접 들고 있으면 도메인 로직 사이에 계측 코드가 섞여
 * 읽기 어려워지고, 생성자에 인프라 타입이 하나 더 붙는다. 그걸 여기로 옮겨서 서비스는
 * "무엇을 잰다"만 알고 "어떻게 재는지"는 모르게 한다.
 */
@Component
public class BidMetrics {

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

    public BidMetrics(MeterRegistry registry) {
        this.lockWait = registry.timer("upbid.bid.lock.wait");
    }

    /**
     * 물품 행 락을 잡는 동안 걸린 시간을 잰다.
     *
     * <p><b>예외가 나도 기록한다</b>({@code Timer.record}가 내부에서 try/finally를 돈다).
     * 그게 중요한 이유는 실패한 요청이야말로 가장 오래 기다린 요청이기 때문이다. 락 획득이
     * {@code innodb_lock_wait_timeout}에 걸린 요청을 빼고 세면 경합이 심해질수록 p95가 오히려
     * 낮게 나오는데, 경합이 극단으로 가는 지점이 정확히 우리가 보려는 곳이다.
     */
    public <T> T recordLockWait(Supplier<T> lockedRead) {
        return lockWait.record(lockedRead);
    }
}
