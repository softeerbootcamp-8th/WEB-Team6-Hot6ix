package com.hot6ix.upbid.domain.deal.repository;

import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealCandidateRepository extends JpaRepository<DealCandidate, Long> {

    /**
     * 같은 마감 이벤트를 두 번 받아도 후보가 한 번만 생기게 하는 멱등 검사다. 순위 유니크
     * 제약이 없으므로 {@code findByIdForUpdate}로 물품 행을 잠근 뒤에 호출한다.
     */
    boolean existsByAuctionItem_AuctionItemId(Long auctionItemId);

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
    boolean existsByAuctionItem_AuctionItemIdAndStatus(Long auctionItemId, DealCandidateStatus status);

    /**
     * 현재 낙찰 권한자를 조회한다. {@code WAITING} 중 순위가 가장 낮은 1행이라는 정의를
     * 쿼리로 옮긴 것이다. 후보 전체를 읽어 메모리에서 고르면 후보 수만큼 비용이 늘어난다.
     */
    @EntityGraph(attributePaths = "bidder")
    Optional<DealCandidate> findFirstByAuctionItem_AuctionItemIdAndStatusOrderByCandidateRankAsc(
            Long auctionItemId, DealCandidateStatus status);

    /** 실패한 후보 다음으로 차례가 넘어갈 후보를 찾는다. */
    @EntityGraph(attributePaths = "bidder")
    Optional<DealCandidate> findFirstByAuctionItem_AuctionItemIdAndStatusAndCandidateRankGreaterThanOrderByCandidateRankAsc(
            Long auctionItemId, DealCandidateStatus status, Integer candidateRank);

    /** 경로로 받은 물품에 속한 후보만 찾는다. 다른 물품의 후보 ID로는 조회되지 않는다. */
    @EntityGraph(attributePaths = "bidder")
    Optional<DealCandidate> findByDealCandidateIdAndAuctionItem_AuctionItemId(
            Long dealCandidateId, Long auctionItemId);

    /**
     * 후보를 순위 오름차순으로 모두 조회한다. 거래 상태 변경에 필요한 판단 재료
     * ({@code COMPLETED} 존재 여부, 현재 낙찰자, 대상 후보)를 쿼리 한 번으로 얻는다.
     * {@code bidder}는 알림에 회원 ID가 필요해 fetch join한다.
     */
    @Query("select dc from DealCandidate dc "
            + "join fetch dc.bidder "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "order by dc.candidateRank asc")
    List<DealCandidate> findAllByAuctionItemId(@Param("auctionItemId") Long auctionItemId);
}
