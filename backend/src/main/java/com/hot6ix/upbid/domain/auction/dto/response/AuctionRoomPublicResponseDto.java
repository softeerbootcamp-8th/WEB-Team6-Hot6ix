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
        // 방이 종료된 시각. 종료 화면이 "종료 {날짜}"를 그리는 데 쓴다. 종료 전에는 null이다.
        LocalDateTime closedAt,
        Long itemCount,
        // 입찰 도메인 담당자가 별도로 채울 필드라 여기서는 항상 null이다.
        Long participantCount,
        // 보는 사람이 이 방의 주인인지. 화면이 판매자 조작(물품 추가·빼기·시작)을 띄울 근거이며,
        // 실제 권한은 조작 API가 다시 검증한다. 로그인하지 않았으면 false.
        boolean isOwner
) {
    public static AuctionRoomPublicResponseDto from(AuctionRoom auctionRoom, Long itemCount, boolean isOwner) {
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
                .closedAt(auctionRoom.getClosedAt())
                .itemCount(itemCount)
                .isOwner(isOwner)
                .build();
    }
}
