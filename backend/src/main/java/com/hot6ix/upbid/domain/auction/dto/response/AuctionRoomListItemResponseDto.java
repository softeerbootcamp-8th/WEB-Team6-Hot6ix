package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import java.time.LocalDateTime;
import lombok.Builder;

/** 판매자 본인의 "내 경매방" 목록 카드 하나. */
@Builder
public record AuctionRoomListItemResponseDto(
        Long auctionRoomId,
        String name,
        String coverImageUrl,
        AuctionRoomStatus status,
        LocalDateTime createdAt,
        Long itemCount,
        /** 참여자를 기록하는 코드가 아직 없어 항상 null이다. 입장 기록이 붙으면 채운다(이슈 #117). */
        Long participantCount
) {

    /**
     * 목록 조회 JPQL이 쓰는 생성자. {@code participantCount}를 뺀 이유는 JPQL 생성자 표현식에
     * bare {@code null}을 넣으면 타입 추론이 안 되기 때문이다.
     */
    public AuctionRoomListItemResponseDto(Long auctionRoomId, String name, String coverImageUrl,
                                          AuctionRoomStatus status, LocalDateTime createdAt, Long itemCount) {
        this(auctionRoomId, name, coverImageUrl, status, createdAt, itemCount, null);
    }

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
