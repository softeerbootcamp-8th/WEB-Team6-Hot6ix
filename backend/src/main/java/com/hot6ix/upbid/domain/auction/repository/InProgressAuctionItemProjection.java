package com.hot6ix.upbid.domain.auction.repository;

import java.time.LocalDateTime;

/**
 * 진행 중인 물품 한 줄. 기동과 주기 재동기화가 <b>마감 예약과 마감 임박 알림 예약을 함께</b>
 * 채우는 데 쓰며, 그 두 가지에 필요한 값만 담는다.
 *
 * <p>엔티티를 읽지 않는 것은 실제 마감이 {@code AuctionItemCloseService}에서 행 락을 걸고
 * 다시 읽기 때문이다. 여기서 상태나 현재가를 들고 있어봐야 마감이 도는 시점에는 이미 낡은
 * 값이라, 아예 안 갖는 편이 낫다.
 *
 * @param endAt                    마감 시각. 마감 예약을 걸 시각이자 알림 시각의 기준
 * @param softCloseTriggerSeconds  경매방의 연장 트리거. <b>알림 시각을 여기서 계산한다</b>
 *                                 ({@code endAt - 이 값}). 물품이 아니라 방에 있는 값이라
 *                                 조인이 하나 붙는다
 * @param notifiedAt               마지막으로 알린 시각. <b>이미 알린 물품을 다시 넣지 않으려고</b>
 *                                 읽는다. 안 읽으면 재동기화가 주기마다 이미 끝난 알림을 큐에
 *                                 넣고, 폴러가 집어서 DB 를 읽고 버리는 한 바퀴가 계속 돈다
 */
public record InProgressAuctionItemProjection(
        Long auctionItemId,
        LocalDateTime endAt,
        Integer softCloseTriggerSeconds,
        LocalDateTime notifiedAt
) {

    /**
     * 이 물품에 마감 임박 알림을 예약해야 하는지. <b>연장 설정이 없으면 알리지 않고</b>, 이미
     * 이번 알림 시각 것을 알렸으면 넣지 않는다.
     *
     * <p>{@code notifiedAt < 알림 시각}까지 허용하는 것이 Soft Close 연장을 받아낸다. 연장되면
     * 알림 시각도 함께 밀리므로, 지난번에 알린 시각이 새 알림 시각보다 앞이면 연장 구간을
     * 벗어났다 다시 들어온 것이라 한 번 더 알려야 한다.
     */
    public boolean needsClosingSoonSchedule() {

        if (softCloseTriggerSeconds == null) {
            return false;
        }

        return notifiedAt == null || notifiedAt.isBefore(notifyAt());
    }

    /**
     * 마감 임박 알림 시각. 연장 구간이 열리는 순간이며
     * {@code ItemClosingSoonService}가 쓰는 식과 같다.
     *
     * <p>{@link #needsClosingSoonSchedule()}이 {@code true}일 때만 부른다 — 연장 설정이 없는
     * 물품에는 이 시각이 없다.
     */
    public LocalDateTime notifyAt() {
        return endAt.minusSeconds(softCloseTriggerSeconds);
    }
}
