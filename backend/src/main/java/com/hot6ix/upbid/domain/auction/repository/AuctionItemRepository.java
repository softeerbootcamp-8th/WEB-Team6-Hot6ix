package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionItemRepository extends JpaRepository<AuctionItem, Long> {

    /**
     * 목록 정렬 순위를 만드는 식. 진행중 → 대기 → 낙찰 → 유찰 순이며
     * {@code AuctionItemStatus} 선언 순서와 다르므로 식으로 계산한다.
     * 별칭 {@code ai}에 묶여 있으므로 다른 별칭을 쓰는 쿼리에 그대로 가져다 쓸 수 없다.
     * 네 상태를 모두 명시하고 {@code else}는 5로 둔다. 상태가 추가되면 유찰과 섞이지 않고
     * 목록 맨 뒤로 밀려 눈에 띈다.
     */
    String STATUS_RANK = "case ai.status"
            + " when com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.IN_PROGRESS then 1"
            + " when com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.READY then 2"
            + " when com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.SOLD then 3"
            + " when com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.FAILED then 4"
            + " else 5 end";

    /**
     * 목록 조회 상한. 페이지네이션이 없으므로 한 응답의 크기를 여기서만 막는다.
     * 물품이 이보다 많은 경매방이 생기면 뒤쪽이 조용히 잘리므로, 그때는 상한을 올릴 게
     * 아니라 페이지네이션을 다시 넣어야 한다.
     */
    int MAX_SUMMARY_SIZE = 100;

    /**
     * 경매방의 물품 목록을 상태 우선 순서로 최대 {@link #MAX_SUMMARY_SIZE}건 조회한다.
     * 페이지네이션은 두지 않는다. 한 경매방의 물품이 한 응답에 다 담기는 규모라는 전제이며,
     * 쿼리 한 번이 곧 하나의 스냅샷이라 페이지 경계에서 항목이 밀리거나 빠지지 않는다.
     */
    default List<AuctionItemSummaryResponseDto> findSummaries(Long auctionRoomId) {
        return findSummaries(auctionRoomId, Limit.of(MAX_SUMMARY_SIZE));
    }

    @Query("select new com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto("
            + "  ai.auctionItemId, p.name, p.imageUrl, ai.currentPrice, ai.status, ai.endAt) "
            + "from AuctionItem ai "
            + "join ai.product p "
            + "where ai.auctionRoom.auctionRoomId = :auctionRoomId "
            + "order by " + STATUS_RANK + " asc, ai.auctionItemId asc")
    List<AuctionItemSummaryResponseDto> findSummaries(
            @Param("auctionRoomId") Long auctionRoomId, Limit limit);

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

    /**
     * 물품 행에 쓰기 락을 걸고 조회한다. 거래 상태 변경은 후보를 읽고 검사한 뒤 쓰는
     * 흐름이라, 상태 검사만으로는 동시 요청을 막지 못한다 — 실패와 성사가 함께 들어오면
     * 성사된 거래가 뒤집힌다. 트랜잭션 안에서만 호출해야 한다.
     *
     * <p>연관 엔티티를 fetch join하지 않는다. MySQL {@code FOR UPDATE}는 조인된 행까지
     * 잠가서 같은 판매자의 다른 물품 거래 처리끼리 막힌다.
     *
     * @param auctionItemId 잠글 물품 ID
     * @return 물품. 없으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ai from AuctionItem ai where ai.auctionItemId = :auctionItemId")
    Optional<AuctionItem> findByIdForUpdate(@Param("auctionItemId") Long auctionItemId);
}
