package com.hot6ix.upbid.domain.bid.repository;

import com.hot6ix.upbid.domain.bid.entity.Bid;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, Long> {

    /**
     * 물품들의 상위 입찰자를 물품당 {@code limit}명까지 조회한다. 입찰자 한 명이 한 줄이고
     * 금액은 그 사람이 그 물품에 넣은 최고가다.
     *
     * <p>순위는 금액만으로 정한다. 한 물품에서 같은 금액은 한 번만 입찰될 수 있으므로
     * ({@code uk_bids_item_amount}) 금액 순서가 곧 시각 순서다. 마지막 키로 둔
     * {@code bidder_user_id}는 데이터가 어떻든 결과가 하나로 정해지게 하는 보험이다.
     *
     * <p>탈퇴 회원은 순위를 매기기 <b>전에</b> 조인으로 뺀다. 순위를 매긴 뒤에 빼면
     * 번호에 구멍이 생긴다. {@code insertCandidatesFromBids}와 같은 이유다.
     *
     * <p>물품 ID가 비면 쿼리하지 않는다. MySQL은 빈 {@code IN ()}을 문법 오류로 본다.
     *
     * @return 물품 ID 오름차순, 물품 안에서는 순위 오름차순. 입찰이 없는 물품은 행이 없다
     */
    default List<TopBidderProjection> findTopBidders(List<Long> auctionItemIds, int limit) {
        if (auctionItemIds.isEmpty()) {
            return List.of();
        }
        return findTopBidderRows(auctionItemIds, limit);
    }

    @Query(value = """
            SELECT * FROM (
                SELECT b.auction_item_id AS auctionItemId,
                       u.nickname        AS nickname,
                       MAX(b.amount)     AS amount,
                       ROW_NUMBER() OVER (
                           PARTITION BY b.auction_item_id
                           ORDER BY MAX(b.amount) DESC, b.bidder_user_id ASC) AS rankNo
                  FROM bids b
                  JOIN users u
                    ON u.user_id = b.bidder_user_id
                   AND u.deleted_at IS NULL
                 WHERE b.auction_item_id IN (:auctionItemIds)
                 GROUP BY b.auction_item_id, b.bidder_user_id, u.nickname) ranked
             WHERE rankNo <= :limit
             ORDER BY auctionItemId ASC, rankNo ASC
            """, nativeQuery = true)
    List<TopBidderProjection> findTopBidderRows(@Param("auctionItemIds") List<Long> auctionItemIds,
                                                @Param("limit") int limit);
}
