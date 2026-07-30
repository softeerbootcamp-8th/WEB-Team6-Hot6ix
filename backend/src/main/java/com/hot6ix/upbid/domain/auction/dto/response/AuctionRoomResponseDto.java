package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record AuctionRoomResponseDto(
        Long auctionRoomId,
        String name,
        String coverImageUrl,
        String description,
        String liveUrl,
        AuctionRoomStatus status,
        Integer softCloseTriggerSeconds,
        Integer softCloseExtendSeconds,
        String sellerStoreName,
        String sellerStoreImageUrl,
        LocalDateTime createdAt,
        // itemCount는 이 PR 후속 커밋에서 채운다. participantCount는 입찰 도메인 담당자가
        // 별도로 채울 필드라 여기서는 항상 null이다.
        Long itemCount,
        Long participantCount
) {
    public static AuctionRoomResponseDto from(AuctionRoom auctionRoom) {
        return AuctionRoomResponseDto.builder()
                .auctionRoomId(auctionRoom.getAuctionRoomId())
                .name(auctionRoom.getName())
                .coverImageUrl(auctionRoom.getCoverImageUrl())
                .description(auctionRoom.getDescription())
                .liveUrl(auctionRoom.getLiveUrl())
                .status(auctionRoom.getStatus())
                .softCloseTriggerSeconds(auctionRoom.getSoftCloseTriggerSeconds())
                .softCloseExtendSeconds(auctionRoom.getSoftCloseExtendSeconds())
                .sellerStoreName(auctionRoom.getSellerProfile().getStoreName())
                .sellerStoreImageUrl(auctionRoom.getSellerProfile().getStoreImageUrl())
                .createdAt(auctionRoom.getCreatedAt())
                .build();
    }
}
