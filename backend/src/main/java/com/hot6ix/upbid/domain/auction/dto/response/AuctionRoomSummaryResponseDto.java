package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import lombok.Builder;

@Builder
public record AuctionRoomSummaryResponseDto(
        Long auctionRoomId,
        String name,
        String coverImageUrl,
        String sellerStoreName,
        String description,
        // itemCount·participantCount는 이 DTO를 실제로 쓸 목록 조회 API가 생길 때 채운다.
        Long itemCount,
        Long participantCount
) {
    public static AuctionRoomSummaryResponseDto from(AuctionRoom auctionRoom) {
        return AuctionRoomSummaryResponseDto.builder()
                .auctionRoomId(auctionRoom.getAuctionRoomId())
                .name(auctionRoom.getName())
                .coverImageUrl(auctionRoom.getCoverImageUrl())
                .sellerStoreName(auctionRoom.getSellerProfile().getStoreName())
                .description(auctionRoom.getDescription())
                .build();
    }
}
