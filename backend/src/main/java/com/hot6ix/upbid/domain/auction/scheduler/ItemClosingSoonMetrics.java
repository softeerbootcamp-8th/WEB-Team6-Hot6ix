package com.hot6ix.upbid.domain.auction.scheduler;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 마감 임박 알림 계측만 아는 객체. 분리한 이유는 {@link AuctionCloseMetrics}와 같다.
 *
 * <p><b>밀린 예약 수 하나만 낸다.</b> 마감은 실행 지연과 소요 시간까지 재는데, 그건 실행
 * 스레드를 몇 개 둘지 정하려고 재는 값이다. 알림은 폴링 스레드가 직접 처리해서 정할 스레드가
 * 없으므로 그 둘은 볼 데가 없다.
 */
@Component
@RequiredArgsConstructor
public class ItemClosingSoonMetrics {

    private final MeterRegistry registry;

    /**
     * 알림 시각이 지났는데 아직 처리되지 않은 예약 수를 게이지로 내보낸다.
     *
     * <p>이 값이 0 에서 안 내려오면 알림이 밀리고 있다는 뜻이다. 실행 지연으로는 그것을 못
     * 본다 — 주기적으로 훑어 집는 방식이라 폴링 주기가 지연의 바닥으로 깔려서, 밀려도 값이
     * 크게 안 움직인다.
     *
     * @param backlogSize 스크랩할 때마다 불린다. Redis 를 못 읽으면 {@code -1}을 준다
     */
    public void bindBacklog(Supplier<Number> backlogSize) {
        Gauge.builder("upbid.auction.closing-soon.backlog", backlogSize)
                .description("알림 시각이 지났는데 아직 처리되지 않은 마감 임박 알림 예약 수")
                .register(registry);
    }
}
