package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoomRole;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import java.time.LocalDateTime;
import lombok.Builder;

/** "내 경매방" 목록 카드 하나. 내가 만든 방과 참여한 방이 같은 모양으로 나온다. */
@Builder
public record AuctionRoomListItemResponseDto(
        Long auctionRoomId,
        /** 카드를 눌러 방으로 들어갈 때 쓰는 공개 식별자. 방 화면은 숫자 ID로 열 수 없다. */
        String shareCode,
        String name,
        String coverImageUrl,
        AuctionRoomStatus status,
        AuctionRoomRole role,
        String storeName,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        Long itemCount,
        // LIVE 상태인 경매방에 한정하여 제공한다.
        Long participantCount
) {

    /**
     * 목록 조회 JPQL이 쓰는 생성자.
     *
     * <p>{@code role}을 {@code String}으로 받는 이유는 JPQL {@code case} 표현식이 문자열을
     * 주기 때문이다. {@code participantCount}는 Service가 뒤에 채우므로 여기서는 뺀다 —
     * JPQL 생성자 표현식에 bare {@code null}을 넣으면 타입 추론이 안 된다.
     */
    public AuctionRoomListItemResponseDto(Long auctionRoomId, String shareCode, String name,
                                          String coverImageUrl, AuctionRoomStatus status, String role,
                                          String storeName, LocalDateTime createdAt,
                                          LocalDateTime closedAt, Long itemCount) {
        this(auctionRoomId, shareCode, name, coverImageUrl, status, AuctionRoomRole.valueOf(role),
                storeName, createdAt, closedAt, itemCount, null);
    }

    /** 참여자 수만 바꾼 복사본. record라 값을 못 고쳐서 새로 만든다. */
    public AuctionRoomListItemResponseDto withParticipantCount(Long participantCount) {
        return new AuctionRoomListItemResponseDto(auctionRoomId, shareCode, name, coverImageUrl,
                status, role, storeName, createdAt, closedAt, itemCount, participantCount);
    }
}
