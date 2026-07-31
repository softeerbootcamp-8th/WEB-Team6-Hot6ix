package com.hot6ix.upbid.domain.deal.repository;

import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealCandidateRepository extends JpaRepository<DealCandidate, Long> {

    /**
     * JPQL은 enum을 정규화된 이름으로 써야 한다. 여러 쿼리가 공유하므로 상수로 뺀다.
     * {@link DealCandidateStatus} 상수명을 바꾸면 여기도 함께 고쳐야 한다.
     */
    String WAITING = "com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus.WAITING";
    String COMPLETED = "com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus.COMPLETED";

    /**
     * 같은 마감 이벤트를 두 번 받아도 후보가 한 번만 생기게 하는 멱등 검사다. 순위 유니크
     * 제약이 없으므로 {@code findByIdForUpdate}로 물품 행을 잠근 뒤에 호출한다.
     */
    @Query("select count(dc) > 0 from DealCandidate dc "
            + "where dc.auctionItem.auctionItemId = :auctionItemId")
    boolean existsCandidate(@Param("auctionItemId") Long auctionItemId);

    /**
     * 물품의 입찰 이력을 낙찰 후보로 한 번에 옮긴다. 입찰자 한 명이 한 행이고 금액은 그 사람의
     * 최고가다. 순위는 1부터 연속하며 마감 이후 바뀌지 않는다.
     *
     * <p>순위는 금액만으로 정한다. 한 물품에서 같은 금액은 한 번만 입찰될 수 있으므로
     * ({@code uk_bids_item_amount}) 금액 순서가 곧 시각 순서다. 마지막 키로 둔
     * {@code bidder_user_id}는 데이터가 어떻든 결과가 하나로 정해지게 하는 보험이다.
     *
     * <p>탈퇴 회원은 순위를 매기기 전에 조인으로 빼야 순위 번호에 구멍이 생기지 않는다.
     *
     * <p>{@code status}를 문자열로 박으므로 {@link DealCandidateStatus} 상수명을 바꾸면 이 SQL이
     * 조용히 깨진다.
     *
     * @return 삽입된 행 수. <b>0이면 순위에 들 입찰이 없었다는 뜻</b>이다
     */
    @Modifying
    @Query(value = """
            INSERT INTO deal_candidates
                (auction_item_id, bidder_user_id, candidate_rank, bid_amount, status)
            SELECT :auctionItemId,
                   t.bidder_user_id,
                   ROW_NUMBER() OVER (ORDER BY t.amount DESC, t.bidder_user_id ASC),
                   t.amount,
                   'WAITING'
              FROM (SELECT b.bidder_user_id,
                           MAX(b.amount) AS amount
                      FROM bids b
                      JOIN users u
                        ON u.user_id = b.bidder_user_id
                       AND u.deleted_at IS NULL
                     WHERE b.auction_item_id = :auctionItemId
                     GROUP BY b.bidder_user_id) t
            """, nativeQuery = true)
    int insertCandidatesFromBids(@Param("auctionItemId") Long auctionItemId);

    /** 거래가 이미 끝났는지 판단한다. {@code COMPLETED} 후보가 있으면 더 바꿀 수 없다. */
    @Query("select count(dc) > 0 from DealCandidate dc "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "and dc.status = " + COMPLETED)
    boolean existsCompletedCandidate(@Param("auctionItemId") Long auctionItemId);

    /**
     * 현재 낙찰 권한자를 조회한다. {@code WAITING} 중 순위가 가장 낮은 1행이라는 정의를 쿼리로
     * 옮긴 것이다. 후보 전체를 읽어 메모리에서 고르면 후보 수만큼 비용이 늘어난다.
     */
    @Query("select dc from DealCandidate dc "
            + "join fetch dc.bidder "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "and dc.status = " + WAITING + " "
            + "order by dc.candidateRank asc limit 1")
    Optional<DealCandidate> findCurrentWinner(@Param("auctionItemId") Long auctionItemId);

    /** 대상보다 앞 순번의 후보가 아직 기다리고 있으면, 지금은 그 후보의 차례다. */
    @Query("select count(dc) > 0 from DealCandidate dc "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "and dc.status = " + WAITING + " "
            + "and dc.candidateRank < :candidateRank")
    boolean existsWaitingCandidateBefore(@Param("auctionItemId") Long auctionItemId,
                                         @Param("candidateRank") Integer candidateRank);

    /** 실패한 후보 다음으로 차례가 넘어갈 후보를 찾는다. */
    @Query("select dc from DealCandidate dc "
            + "join fetch dc.bidder "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "and dc.status = " + WAITING + " "
            + "and dc.candidateRank > :candidateRank "
            + "order by dc.candidateRank asc limit 1")
    Optional<DealCandidate> findNextWaitingCandidate(@Param("auctionItemId") Long auctionItemId,
                                                     @Param("candidateRank") Integer candidateRank);

    /** 경로로 받은 물품에 속한 후보만 찾는다. 다른 물품의 후보 ID로는 조회되지 않는다. */
    @Query("select dc from DealCandidate dc "
            + "join fetch dc.bidder "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "and dc.dealCandidateId = :dealCandidateId")
    Optional<DealCandidate> findCandidate(@Param("auctionItemId") Long auctionItemId,
                                          @Param("dealCandidateId") Long dealCandidateId);
}
