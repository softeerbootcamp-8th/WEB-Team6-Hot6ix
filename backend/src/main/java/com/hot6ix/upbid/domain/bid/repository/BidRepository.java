package com.hot6ix.upbid.domain.bid.repository;

import com.hot6ix.upbid.domain.bid.dto.BidderRankProjection;
import com.hot6ix.upbid.domain.bid.entity.Bid;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, Long> {

    /**
     * 순위 쿼리 앞부분. 제외 조건이 들어갈 자리가 중간이라 둘로 나눠 두 쿼리가 같은 순위
     * 규칙을 공유하게 한다. 복사해 쓰면 한쪽만 고쳐져 낙찰 순위가 조용히 달라진다.
     */
    String RANKED_BIDDERS_HEAD = """
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
                      JOIN users u
                        ON u.user_id = b.bidder_user_id
                       AND u.deleted_at IS NULL
                     WHERE b.auction_item_id = :auctionItemId
            """;

    String RANKED_BIDDERS_TAIL = """
                           ) t
             WHERE t.rn = 1
             ORDER BY t.amount DESC, t.accepted_at ASC, t.bidder_user_id ASC
             LIMIT :limit
            """;

    /**
     * 입찰자별 최고가 1건만 뽑아 금액 내림차순으로 최대 {@code limit}명을 돌려준다.
     * {@code group by + max}가 아니라 {@code ROW_NUMBER()}인 이유는 전자의 tie-break 시각이
     * 최고가 시점이 아니라 첫 입찰 시점이 되어 순위가 틀어지기 때문이다. 탈퇴 회원은 순위를
     * 매긴 뒤 걸러내면 번호에 구멍이 생기므로 조인으로 미리 제외한다.
     */
    @Query(value = RANKED_BIDDERS_HEAD + RANKED_BIDDERS_TAIL, nativeQuery = true)
    List<BidderRankProjection> findTopBidders(@Param("auctionItemId") Long auctionItemId,
                                             @Param("limit") int limit);

    /**
     * {@link #findTopBidders}와 같은 규칙으로, 이미 후보가 된 입찰자를 빼고 다음 순위를
     * 이어서 조회한다. {@code OFFSET}을 쓰지 않는 이유는 탈퇴로 결과 집합이 줄면 같은
     * offset이 한 명을 건너뛰기 때문이다.
     * {@code excludedBidderUserIds}가 비면 {@code NOT IN (null)}이 되어 전부 걸러진다.
     */
    @Query(value = RANKED_BIDDERS_HEAD
            + "       AND b.bidder_user_id NOT IN (:excludedBidderUserIds)\n"
            + RANKED_BIDDERS_TAIL, nativeQuery = true)
    List<BidderRankProjection> findTopBiddersExcluding(
            @Param("auctionItemId") Long auctionItemId,
            @Param("excludedBidderUserIds") Collection<Long> excludedBidderUserIds,
            @Param("limit") int limit);
}
