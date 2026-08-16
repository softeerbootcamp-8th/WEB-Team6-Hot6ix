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
        Close close,
        ClosingSoon closingSoon,
        Room room
) {

    /**
     * 물품이 모두 마감된 경매방을 자동으로 종료하는 데 쓰는 값들.
     *
     * @param idleCloseAfter     마지막 물품이 마감되고 이만큼 지나면 종료 대상이다. 판매자가
     *                           방송을 마치고 종료를 안 눌렀을 때 서버가 대신 정리하는 시간이라,
     *                           "잠깐 자리를 비운 것"과 갈릴 만큼 넉넉해야 한다
     * @param idleCloseBatchSize 한 tick 에 종료할 최대 방 수. <b>여기서 잘린 방은 다음 주기로
     *                           넘어간다.</b> 상한을 두는 것은 방이 한꺼번에 밀렸을 때 한 tick 이
     *                           DB 를 오래 붙잡지 않게 하려는 것이다
     */
    public record Room(
            Duration idleCloseAfter,
            int idleCloseBatchSize
    ) {
    }

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

    /**
     * 마감 임박 알림 예약을 Redis 에서 꺼내 실행하는 데 쓰는 값들. 마감과 <b>따로 두는</b>
     * 것은 알림 한 건이 훨씬 가벼워서(조회 하나, UPDATE 하나, SSE 발행) 같은 값으로 맞출
     * 이유가 없고, 한쪽만 조여 볼 수 있어야 하기 때문이다.
     *
     * <p>마감에 있는 {@code workerPoolSize}와 {@code queueCapacity}가 여기엔 없다. 알림은
     * 폴링 스레드가 직접 처리해서 실행 풀을 두지 않는다.
     *
     * @param claimBatchSize    한 번에 집어올 최대 예약 수. 마감과 같은 이유로 상한을 둔다
     * @param visibilityTimeout 집어온 예약을 얼마나 미뤄 둘지. <b>알림에서는 이 값이 중복
     *                          발송으로 이어지지 않는다</b> — 미뤄 둔 시각에 다시 떠올라도
     *                          {@code notified_at} 이 걸러 준다. 그래서 마감보다 짧게 잡아
     *                          죽은 서버가 쥐고 있던 알림을 빨리 되찾는 쪽을 택한다
     */
    public record ClosingSoon(
            int claimBatchSize,
            Duration visibilityTimeout
    ) {
    }
}
