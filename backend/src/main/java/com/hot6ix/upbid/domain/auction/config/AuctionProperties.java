package com.hot6ix.upbid.domain.auction.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 경매 규칙 중 <b>값을 바꿔 가며 재야 하는</b> 것만 밖으로 뺀 설정.
 * 기본값은 {@code application.yaml}에 있고 서비스 규칙 그대로다.
 */
@ConfigurationProperties(prefix = "upbid.auction")
public record AuctionProperties(
        int maxInProgressPerRoom,
        Close close
) {

    /**
     * 마감 예약을 Redis 에서 꺼내 실행하는 데 쓰는 값들. 전부 측정하고 정할 값이라 밖으로 뺐다.
     *
     * @param claimBatchSize    한 번에 집어올 최대 예약 수. <b>Redis 를 한 번에 얼마나 붙잡을지를
     *                          정한다</b> — 스크립트가 도는 동안 Redis 는 다른 명령을 못 받는다
     * @param visibilityTimeout 집어온 예약을 얼마나 미뤄 둘지. <b>마감 한 건에 걸리는 시간보다
     *                          넉넉해야 한다.</b> 처리 도중에 다시 떠오르면 다른 서버가 같은
     *                          물품에 달라붙어 행 락 경합만 늘어난다. 반대로 너무 크면 서버가
     *                          죽었을 때 그 예약을 남이 집어가는 게 그만큼 늦어진다
     * @param workerPoolSize    마감을 실제로 실행하는 스레드 수. <b>커넥션 풀의 절반을 넘기지
     *                          않는다</b> — 마감 뒤 낙찰 후보 생성이 커넥션을 따로 잡아서, 넘기면
     *                          사용자 입찰이 커넥션을 못 얻고 5xx 가 난다. 배포에서는 일꾼이
     *                          최대 1개만 돌아서 지금 값도 남는다
     * @param queueCapacity     일꾼을 기다리는 마감이 몇 건까지 쌓일 수 있는지. <b>집는 개수는
     *                          여기 남은 자리에 맞춰지므로</b>, 이 값이 마감이 몰렸을 때 한 번에
     *                          얼마나 받아낼지를 정한다
     */
    public record Close(
            int claimBatchSize,
            Duration visibilityTimeout,
            int workerPoolSize,
            int queueCapacity
    ) {
    }
}
