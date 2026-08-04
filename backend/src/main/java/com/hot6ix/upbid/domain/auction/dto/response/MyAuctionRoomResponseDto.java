package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.deal.entity.DealRole;
import java.time.LocalDateTime;

/**
 * 내 경매방 목록의 카드 하나. 내가 만든 방과 내가 입찰한 방이 한 목록에 섞여 오고,
 * {@code role}로 갈린다.
 *
 * <p>상태별 구분(진행 중·준비 중·종료)과 검색은 화면이 이 목록을 받아 직접 한다. 그래서
 * 필터 파라미터도, 상태별 건수도 응답에 없다.
 *
 * <p>참여자 수는 담지 않는다. 종료된 방의 참여자 수는 입찰한 사람 수인지 방송을 보던 사람
 * 수인지 구분되지 않아 뜻이 하나로 읽히지 않고, 라이브 방의 접속자 수는 SSE가 가진 값이라
 * 조회 응답에 실을 경로가 아직 없다.
 *
 * @param role     {@code SELLER}면 내가 만든 방, {@code BUYER}면 내가 입찰한 방
 * @param closedAt 방이 닫힌 시각. 방 종료 상태 전이가 아직 없어 지금은 항상 {@code null}이다
 */
public record MyAuctionRoomResponseDto(
        Long auctionRoomId,
        String name,
        String coverImageUrl,
        String sellerStoreName,
        AuctionRoomStatus status,
        DealRole role,
        Long itemCount,
        LocalDateTime closedAt,
        LocalDateTime createdAt
) {
}
