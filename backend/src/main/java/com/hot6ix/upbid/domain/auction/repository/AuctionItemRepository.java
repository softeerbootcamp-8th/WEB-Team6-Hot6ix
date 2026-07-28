package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionItemRepository extends JpaRepository<AuctionItem, Long> {

    /**
     * 목록 정렬 순위를 만드는 식. 진행중 → 대기 → 마감 → 유찰 순이며
     * {@code AuctionItemStatus} 선언 순서와 다르므로 식으로 계산한다.
     */
    String STATUS_RANK = "case ai.status"
            + " when com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.IN_PROGRESS then 1"
            + " when com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.READY then 2"
            + " when com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.SOLD then 3"
            + " else 4 end";

    /**
     * 경매방의 물품 목록을 상태 우선 순서로 전부 조회한다.
     * 페이지네이션은 두지 않는다. 한 경매방의 물품이 한 응답에 다 담기는 규모라는 전제이며,
     * 쿼리 한 번이 곧 하나의 스냅샷이라 페이지 경계에서 항목이 밀리거나 빠지지 않는다.
     */
    @Query("select new com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto("
            + "  ai.auctionItemId, p.name, p.imageUrl, ai.currentPrice, ai.status, ai.endAt) "
            + "from AuctionItem ai "
            + "join ai.product p "
            + "where ai.auctionRoom.auctionRoomId = :auctionRoomId "
            + "order by " + STATUS_RANK + " asc, ai.auctionItemId asc")
    List<AuctionItemSummaryResponseDto> findSummaries(@Param("auctionRoomId") Long auctionRoomId);

    /**
     * 물품 상세를 조회한다. 상태로 거르지 않으므로 낙찰·유찰된 물품도 조회된다.
     */
    @Query("select new com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto("
            + "  ai.auctionItemId, ai.auctionRoom.auctionRoomId, "
            + "  p.name, p.description, p.imageUrl, p.referenceUrl, "
            + "  ai.currentPrice, ai.bidIncrement, ai.status, ai.endAt) "
            + "from AuctionItem ai "
            + "join ai.product p "
            + "where ai.auctionItemId = :auctionItemId")
    Optional<AuctionItemDetailResponseDto> findDetail(@Param("auctionItemId") Long auctionItemId);
}
