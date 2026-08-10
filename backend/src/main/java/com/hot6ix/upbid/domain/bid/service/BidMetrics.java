package com.hot6ix.upbid.domain.bid.service;

import com.hot6ix.upbid.global.common.LockTimer;
import io.micrometer.core.instrument.MeterRegistry;
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

    /** {@code upbid.bid.lock.wait}와 {@code upbid.bid.lock.hold}. 마감 쪽과 같은 도구를 쓴다. */
    private final LockTimer lock;

    public BidMetrics(MeterRegistry registry) {
        this.lock = new LockTimer(registry, "upbid.bid");
    }

    /** 물품 행 락을 잡는 동안 걸린 시간과 잡고 있던 시간을 잰다. */
    public <T> T recordLockWait(Supplier<T> lockedRead) {
        return lock.recordWait(lockedRead);
    }
}
