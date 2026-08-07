package com.hot6ix.upbid.domain.auction.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 물품 마감 계측만 아는 객체. 분리한 이유는 {@code BidMetrics}와 같다.
 */
@Component
public class AuctionCloseMetrics {

    /** 첫 마감이 일어나기 전에도 지표가 보이도록 생성자에서 미리 만든다. */
    private final Timer closeDelay;

    public AuctionCloseMetrics(MeterRegistry registry) {
        this.closeDelay = registry.timer("upbid.auction.close.delay");
    }

    /**
     * 예약한 시각보다 실제 마감이 얼마나 늦었는지 기록한다.
     *
     * <p>예약보다 일찍 실행되면 음수가 나오는데 Timer는 음수를 담지 못하므로 0으로 눕힌다.
     */
    public void recordCloseDelay(long delayMillis) {
        closeDelay.record(Math.max(delayMillis, 0L), TimeUnit.MILLISECONDS);
    }
}
