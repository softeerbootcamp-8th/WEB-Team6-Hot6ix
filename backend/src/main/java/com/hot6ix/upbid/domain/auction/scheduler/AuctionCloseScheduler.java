package com.hot6ix.upbid.domain.auction.scheduler;

import java.time.LocalDateTime;

/**
 * 시작된 물품을 마감 시각에 닫는 예약을 관리한다. 물품 하나당 예약 하나이며, 마감할 물품을
 * 주기적으로 찾아다니지 않는다.
 *
 * <p>예약을 <b>어디에 두는지</b>만 구현이 정한다. 지금은 서버 여러 대가 같이 보도록 Redis 에
 * 담는 {@link RedisAuctionCloseScheduler} 하나다. <b>예전에는 프로세스 메모리에 타이머로
 * 담았는데, 서버를 여러 대로 늘리면서 옮겼다</b>(#289). 그때는 예약을 건 서버가 내려가면
 * 예약도 같이 사라졌고, 연장이 다른 서버의 예약에 닿지 않았다.
 *
 * <p><b>누가 실행하는지는 여기가 아니다.</b> 예약을 꺼내 마감을 부르는 것은
 * {@link AuctionClosePoller}가 맡는다. 예약을 거는 쪽과 실행하는 쪽을 갈라 둔 것은, 서버가
 * 여러 대면 예약을 건 서버와 실행하는 서버가 서로 다를 수 있기 때문이다.
 *
 * <p><b>언제</b> 예약을 걸고 취소할지도 여기가 아니라 {@link AuctionCloseScheduleListener}가
 * 정한다. 그 판단은 예약을 어디에 두든 같아서 구현과 분리했다.
 */
public interface AuctionCloseScheduler {

    /**
     * 지정한 시각에 물품을 마감하도록 예약한다. 이미 지난 시각을 주면 곧바로 실행된다.
     * <b>이미 걸려 있던 예약은 취소하고 갈아 끼운다</b> — Soft Close 연장과 재예약이 이
     * 동작에 기댄다. 취소하지 않으면 옛 예약이 살아남아 원래 시각에 물품을 닫아버린다.
     *
     * @param auctionItemId 마감할 물품의 ID
     * @param endAt         마감 시각. 서버 시간 기준 절대값
     */
    void schedule(Long auctionItemId, LocalDateTime endAt);

    /**
     * <b>예약이 없을 때만</b> 건다. 이미 있으면 그대로 두고 시각을 덮어쓰지 않는다.
     *
     * <p>DB 를 보고 예약을 다시 채우는 {@link AuctionRecoveryRunner}가 쓴다. 덮어쓰면 안 되는
     * 것은 이미 걸려 있는 예약이 DB 보다 최신이거나 실행 중일 수 있기 때문이다. 그 예약에는
     * Soft Close 연장이 반영돼 있거나, 지금 어느 서버가 집어서 처리 중이라는 표시가 들어 있다.
     *
     * @param auctionItemId 마감할 물품의 ID
     * @param endAt         마감 시각. 서버 시간 기준 절대값
     */
    void scheduleIfAbsent(Long auctionItemId, LocalDateTime endAt);

    /**
     * 물품의 마감 예약을 취소한다. 예약이 없거나 이미 실행이 시작됐으면 아무 일도 하지 않는다.
     *
     * @param auctionItemId 예약을 취소할 물품의 ID
     */
    void cancel(Long auctionItemId);
}
