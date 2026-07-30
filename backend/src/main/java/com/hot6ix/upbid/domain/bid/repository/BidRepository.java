package com.hot6ix.upbid.domain.bid.repository;

import com.hot6ix.upbid.domain.bid.dto.BidderRankProjection;
import com.hot6ix.upbid.domain.bid.entity.Bid;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, Long> {

    /**
     * 낙찰 순위를 산정한다. 입찰자별 최고 입찰가 1건만 뽑아 금액이 큰 순으로
     * 최대 {@code limit}명을 돌려준다. 1등이 낙찰자, 그 뒤가 차순위 후보다.
     *
     * <p>같은 사람이 여러 번 입찰해도 한 번만 세야 하므로 {@code ROW_NUMBER()}로
     * 입찰자별 최고가 행만 남긴다. {@code group by bidder + max(amount)}로도 금액은
     * 구할 수 있지만, 금액이 같을 때 쓸 tie-break 시각이 <b>그 최고가를 기록한 시점이
     * 아니라 그 사람의 첫 입찰 시점</b>이 되어 순위가 틀어진다.
     *
     * <p>현재 입찰 규칙(현재가 이하 거절)대로면 금액이 같은 입찰은 아예 생기지 않아
     * tie-break가 필요 없다. 다만 입찰 API가 아직 구현되지 않아 그 불변식이 코드로
     * 보장되지 않고, 순위가 흔들리면 낙찰자가 바뀌는 문제라 금액·시각·id까지 정렬 키를
     * 명시해 <b>어떤 데이터에도 결과가 하나로 정해지게</b> 했다.
     *
     * <p>JPQL에는 window function이 없어 native 쿼리로 작성했다. 윈도우 함수는
     * MySQL 8.0 이상이 필요한데 운영(RDS 8.4)과 테스트(Testcontainers 8.4.9) 모두 만족한다.
     * 중첩 쿼리라 문자열 연결보다 공백 실수가 없는 텍스트 블록을 썼다.
     * {@code LIMIT}을 SQL에 직접 넣어 DB가 5건만 만들고 끝내도록 한다.
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
