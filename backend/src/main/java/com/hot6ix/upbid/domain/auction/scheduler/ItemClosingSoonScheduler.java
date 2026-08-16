package com.hot6ix.upbid.domain.auction.scheduler;

import java.time.LocalDateTime;

/**
 * 마감 임박 알림 예약을 관리한다. {@link AuctionCloseScheduler}와 짝을 이루며 물품 하나당
 * 예약 하나를 갖는다. 마감과 나눠 둔 것은 <b>알림 시각이 마감 시각과 다르고</b>, 마감은
 * 되돌릴 수 없는 상태 변경인데 알림은 그렇지 않아 실패했을 때 사정이 다르기 때문이다.
 *
 * <p>예약을 <b>어디에 두는지</b>만 구현이 정한다. 지금은 서버 여러 대가 같은 목록을 보도록
 * Redis 에 담는 {@link RedisItemClosingSoonScheduler} 하나다. <b>예전에는 프로세스 메모리에
 * 타이머로 담았는데, 서버를 여러 대로 늘리면서 옮겼다</b>(#290).
 *
 * <p><b>누가 실행하는지는 여기가 아니다.</b> 예약을 꺼내 알림을 발행하는 것은
 * {@link ItemClosingSoonPoller}가 맡고, <b>언제</b> 걸고 취소할지는
 * {@link ItemClosingSoonScheduleListener}가 정한다.
 */
public interface ItemClosingSoonScheduler {

    /**
     * 지정한 시각에 마감 임박 알림이 나가도록 예약한다. <b>이미 걸려 있던 예약은 갈아 끼운다</b>
     * — Soft Close 연장으로 알림 시각이 밀릴 때 이 동작에 기댄다.
     *
     * @param auctionItemId 알림을 낼 물품의 ID
     * @param notifyAt      알림 시각({@code endAt - softCloseTriggerSeconds}). 절대값
     */
    void schedule(Long auctionItemId, LocalDateTime notifyAt);

    /**
     * <b>예약이 없을 때만</b> 건다. DB 를 보고 예약을 다시 채우는 {@link AuctionRecoveryRunner}가
     * 쓴다. 이미 있는 줄에는 연장이 반영돼 있거나 지금 어느 서버가 집어서 처리 중이라는 표시가
     * 들어 있는데, DB 의 시각으로 덮으면 그 둘을 되돌린다.
     *
     * <p>마감 예약은 같은 러너가 덮어쓰는데 알림만 안 덮는 것은, <b>알림 시각이 앞으로 당겨져야
     * 하는 경우가 없기 때문</b>이다(#327). 앞당기기는 {@code notified_at} 을 찍어 알림을 아예
     * 끄고, 연장은 시각을 뒤로만 민다. 뒤로 밀린 예약은 일찍 깨어나도 {@code notifyIfDue}가
     * 다시 재워 준다.
     *
     * @param auctionItemId 알림을 낼 물품의 ID
     * @param notifyAt      알림 시각. 절대값
     */
    void scheduleIfAbsent(Long auctionItemId, LocalDateTime notifyAt);

    /**
     * 물품의 알림 예약을 취소한다. 예약이 없으면 아무 일도 하지 않는다.
     *
     * @param auctionItemId 예약을 취소할 물품의 ID
     */
    void cancel(Long auctionItemId);
}
