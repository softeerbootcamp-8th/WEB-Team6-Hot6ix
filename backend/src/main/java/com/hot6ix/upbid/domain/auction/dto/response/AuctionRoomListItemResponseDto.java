package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import java.time.LocalDateTime;
import lombok.Builder;

/** 판매자 본인의 "내 경매방" 목록 카드 하나. 아직 이걸 쓰는 목록 조회 API는 없음(추후 연결 예정). */
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
