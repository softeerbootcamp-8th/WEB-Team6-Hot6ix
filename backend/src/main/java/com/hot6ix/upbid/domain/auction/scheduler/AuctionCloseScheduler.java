package com.hot6ix.upbid.domain.auction.scheduler;

import java.time.LocalDateTime;

/**
 * 시작된 물품을 마감 시각에 닫는 예약을 관리한다. 물품 하나당 예약 하나이며, 마감할 물품을
 * 주기적으로 찾아다니지 않는다.
 *
 * <p>예약을 <b>어디에 두는지</b>는 구현이 정한다. 지금은 프로세스 메모리에 담는
 * {@link InMemoryAuctionCloseScheduler} 하나뿐이고, 서버가 여러 대가 되면 예약 저장소와
 * 실행 자격을 다시 정해야 하는데 그때 갈아 끼울 자리가 이 인터페이스다.
 *
 * <p><b>언제</b> 예약을 걸고 취소할지는 여기가 아니라 {@link AuctionCloseScheduleListener}가
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
     * 물품의 마감 예약을 취소한다. 예약이 없거나 이미 실행이 시작됐으면 아무 일도 하지 않는다.
     *
     * @param auctionItemId 예약을 취소할 물품의 ID
     */
    void cancel(Long auctionItemId);
}
