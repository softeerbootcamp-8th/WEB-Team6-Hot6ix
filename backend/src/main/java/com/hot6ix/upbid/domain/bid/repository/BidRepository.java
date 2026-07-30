package com.hot6ix.upbid.domain.bid.repository;

import com.hot6ix.upbid.domain.bid.dto.BidderRankProjection;
import com.hot6ix.upbid.domain.bid.entity.Bid;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, Long> {

    /**
     * 낙찰 순위를 산정한다. 입찰자별 최고 입찰가 1건만 뽑아 금액이 큰 순으로 최대
     * {@code limit}명을 돌려준다. 1등이 낙찰자, 그 뒤가 차순위 후보다.
     *
     * <p>{@code group by bidder + max(amount)}가 아니라 {@code ROW_NUMBER()}를 쓴 이유는,
     * 전자의 tie-break 시각이 최고가를 기록한 시점이 아니라 그 사람의 첫 입찰 시점이 되어
     * 순위가 틀어지기 때문이다. 금액·시각·id까지 정렬 키를 명시한 것은 입찰 API가 아직 없어
     * "같은 금액은 생기지 않는다"는 불변식이 코드로 보장되지 않기 때문이다.
     *
     * @param auctionItemId 순위를 산정할 물품 ID
     * @param limit         가져올 상위 입찰자 수
     * @return 금액 내림차순 입찰자 목록. 입찰이 없으면 빈 목록
     */
    @Query(value = """
            SELECT t.bidder_user_id AS bidderUserId,
                   t.amount         AS amount
              FROM (SELECT b.bidder_user_id,
                           b.amount,
                           b.accepted_at,
                           ROW_NUMBER() OVER (PARTITION BY b.bidder_user_id
                                              ORDER BY b.amount DESC,
                                                       b.accepted_at ASC,
                                                       b.bid_id ASC) AS rn
                      FROM bids b
                     WHERE b.auction_item_id = :auctionItemId) t
             WHERE t.rn = 1
             ORDER BY t.amount DESC, t.accepted_at ASC, t.bidder_user_id ASC
             LIMIT :limit
            """, nativeQuery = true)
    List<BidderRankProjection> findTopBidders(@Param("auctionItemId") Long auctionItemId,
                                             @Param("limit") int limit);
}
