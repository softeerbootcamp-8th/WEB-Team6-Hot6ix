package com.hot6ix.upbid.domain.auction.dto.cache;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;

/**
 * 공개 조회 응답에서 <b>보는 사람과 무관한 부분</b>만 떼어낸 것. 캐시에 담기는 값이다.
 *
 * <p>{@code isOwner}와 {@code agreedToTerms}는 여기 담지 않는다. 사람마다 달라서 담으면
 * 남의 것을 보여주게 된다. 대신 {@code sellerUserId}를 함께 담아 {@code isOwner}를 쿼리 없이
 * 계산한다 — 그러지 않으면 캐시가 맞아도 주인인지 보려고 방을 다시 읽어야 한다.
 *
 * @param room         응답 그대로. {@code isOwner}는 false, {@code agreedToTerms}는 null이며
 *                     {@link #toResponse}가 보는 사람에 맞게 갈아 끼운다
 * @param sellerUserId 이 방 주인의 회원 ID
 */
public record AuctionRoomPublicSnapshot(
        AuctionRoomPublicResponseDto room,
        Long sellerUserId
) {

    public static AuctionRoomPublicSnapshot from(AuctionRoom auctionRoom, long itemCount) {
        return new AuctionRoomPublicSnapshot(
                AuctionRoomPublicResponseDto.from(auctionRoom, itemCount, false, null),
                auctionRoom.getSellerProfile().getUser().getUserId());
    }

    public Long auctionRoomId() {
        return room.auctionRoomId();
    }

    /**
     * 보는 사람이 이 방의 주인인지 판정한다. 판매자 프로필을 따로 조회하지 않는 것은 프로필이
     * 없는 평범한 구매자까지 조회 예외로 걸러야 하는 흐름을 피하려는 것이다.
     */
    public boolean isOwnedBy(Long viewerUserId) {
        return viewerUserId != null && viewerUserId.equals(sellerUserId);
    }

    /** 보는 사람 몫의 두 값을 끼워 응답을 만든다. record라 새 인스턴스가 나온다. */
    public AuctionRoomPublicResponseDto toResponse(boolean isOwner, Boolean agreedToTerms) {
        return room.toBuilder()
                .isOwner(isOwner)
                .agreedToTerms(agreedToTerms)
                .build();
    }
}
