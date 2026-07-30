package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record AuctionRoomListItemResponseDto(
        Long auctionRoomId,
        String name,
        String coverImageUrl,
        AuctionRoomStatus status,
        LocalDateTime createdAt,
        // itemCount·participantCount는 이 DTO를 실제로 쓸 목록 조회 API가 생길 때 채운다.
        Long itemCount,
        Long participantCount
) {
    public static AuctionRoomListItemResponseDto from(AuctionRoom auctionRoom) {
        return AuctionRoomListItemResponseDto.builder()
                .auctionRoomId(auctionRoom.getAuctionRoomId())
                .name(auctionRoom.getName())
                .coverImageUrl(auctionRoom.getCoverImageUrl())
                .status(auctionRoom.getStatus())
                .createdAt(auctionRoom.getCreatedAt())
                .build();
    }
}
