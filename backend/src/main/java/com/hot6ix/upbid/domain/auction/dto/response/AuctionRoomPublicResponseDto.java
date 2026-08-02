package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import java.time.LocalDateTime;
import lombok.Builder;

/** 누구나 보는 경매방 단독 페이지 정보. 생성 응답(POST) + 공개 조회(GET /{roomId})가 공용으로 쓴다. */
@Builder
public record AuctionRoomPublicResponseDto(
        Long auctionRoomId,
        String name,
        String coverImageUrl,
        String description,
        String liveUrl,
        AuctionRoomStatus status,
        Long bidIncrement,
        Integer softCloseTriggerSeconds,
        Integer softCloseExtendSeconds,
        String sellerStoreName,
        String sellerStoreImageUrl,
        LocalDateTime createdAt,
        Long itemCount,
        // 입찰 도메인 담당자가 별도로 채울 필드라 여기서는 항상 null이다.
        Long participantCount
) {
    public static AuctionRoomPublicResponseDto from(AuctionRoom auctionRoom, Long itemCount) {
        return AuctionRoomPublicResponseDto.builder()
                .auctionRoomId(auctionRoom.getAuctionRoomId())
                .name(auctionRoom.getName())
                .coverImageUrl(auctionRoom.getCoverImageUrl())
                .description(auctionRoom.getDescription())
                .liveUrl(auctionRoom.getLiveUrl())
                .status(auctionRoom.getStatus())
                .bidIncrement(auctionRoom.getBidIncrement())
                .softCloseTriggerSeconds(auctionRoom.getSoftCloseTriggerSeconds())
                .softCloseExtendSeconds(auctionRoom.getSoftCloseExtendSeconds())
                .sellerStoreName(auctionRoom.getSellerProfile().getStoreName())
                .sellerStoreImageUrl(auctionRoom.getSellerProfile().getStoreImageUrl())
                .createdAt(auctionRoom.getCreatedAt())
                .itemCount(itemCount)
                .build();
    }
}
