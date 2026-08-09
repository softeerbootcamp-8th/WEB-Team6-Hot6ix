package com.hot6ix.upbid.domain.deal.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 낙찰 계측만 아는 객체. 분리한 이유는 {@code BidMetrics}와 같다.
 */
@Component
public class DealMetrics {

    /**
     * 낙찰 후보 스냅샷을 만드는 데 걸린 시간.
     *
     * <p>이게 마감 소요 시간에 들어간다. {@code DealCandidateAwardListener}가 커밋 뒤에
     * <b>같은 스레드에서</b> 부르기 때문에, 여기 걸린 시간만큼 스케줄러 스레드가 더 묶인다.
     * 후보를 입찰자 수만큼 만들어서 입찰이 몰린 물품일수록 길어질 것으로 보고 재기 시작했다(#198).
     *
     * <p>첫 낙찰 전에도 지표가 보이도록 생성자에서 미리 만든다.
     */
    private final Timer award;

    public DealMetrics(MeterRegistry registry) {
        this.award = registry.timer("upbid.deal.award");
    }

    /** 예외가 나도 기록하고 예외는 그대로 올린다. 실패한 실행이 가장 오래 걸린 경우일 수 있다. */
    public void recordAward(Runnable award) {
        this.award.record(award);
    }
}
