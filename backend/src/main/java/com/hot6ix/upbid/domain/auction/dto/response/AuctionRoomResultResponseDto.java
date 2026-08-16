package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매방 하나의 물품별 낙찰 결과.
 *
 * <p>낙찰 건수·유찰 건수·총 낙찰액 같은 집계는 두지 않는다. 화면이 {@code items}에서 직접
 * 세는 값이고, 서버가 같은 수를 따로 내리면 둘이 어긋날 때 어느 쪽이 맞는지 알 수 없다.
 *
 * <p>참여자 수도 두지 않는다. 종료된 방의 "참여자 수"는 입찰한 사람의 수인지 방송을 보던
 * 사람의 수인지 구분되지 않아, 뜻이 하나로 읽히지 않는다.
 *
 * @param closedAt 방이 닫힌 시각. 방 종료 상태 전이가 아직 없어 지금은 항상 {@code null}이다
 */
public record AuctionRoomResultResponseDto(
        Long auctionRoomId,
        String name,
        String sellerStoreName,
        AuctionRoomStatus status,
        LocalDateTime closedAt,
        List<AuctionItemResultResponseDto> items
) {
    public static AuctionRoomResultResponseDto of(
            AuctionRoom auctionRoom, List<AuctionItemResultResponseDto> items) {

        return new AuctionRoomResultResponseDto(
                auctionRoom.getAuctionRoomId(),
                auctionRoom.getName(),
                auctionRoom.getSellerProfile().getStoreName(),
                auctionRoom.getStatus(),
                auctionRoom.getClosedAt(),
                items);
    }

    /** 물품 목록만 바꾼 복사본. 캐시에서 읽은 공용 응답에 요청자의 순위를 끼워 넣을 때 쓴다. */
    public AuctionRoomResultResponseDto withItems(List<AuctionItemResultResponseDto> items) {
        return new AuctionRoomResultResponseDto(auctionRoomId, name, sellerStoreName, status, closedAt, items);
    }
}
