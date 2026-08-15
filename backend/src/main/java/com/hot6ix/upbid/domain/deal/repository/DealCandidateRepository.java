package com.hot6ix.upbid.domain.deal.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 상태는 쿼리 문자열에 박지 않고 파라미터로 넘긴다. 문자열로 두면 enum 상수명이 바뀌어도
 * 컴파일러가 잡지 못한다. 대신 상태를 고정한 {@code default} 메서드를 앞에 둬서 호출부에서는
 * 무엇을 찾는 조회인지 이름만으로 드러나게 한다.
 *
 * <p><b>탈퇴한 회원은 낙찰 후보로 취급하지 않는다.</b> 후보 생성 시점에는
 * {@link #insertCandidatesFromBids}가 이미 걸러내고 있었지만, 후보가 된 뒤에 탈퇴하는 경로가
 * 남아 있었다. 그래서 목록·순위 판정·차순위 승계에 모두 같은 조건을 건다. 목록에서만 숨기면
 * 보이지 않는 후보가 낙찰 권한을 쥔 채로 남아 판매자가 거래를 진행할 수 없다.
 *
 * <p>이미 성사·실패로 확정된 후보도 함께 숨겨진다. 확정된 뒤에는 상태를 더 바꿀 수 없으므로
 * 거래가 막히지는 않는다. 다만 {@link #existsCompletedCandidate}는 이 조건을 걸지 않는다 —
 * 화면에 무엇을 보일지와 상태를 바꿔도 되는지는 다른 문제이고, 후자는 사실대로 봐야 한다.
 */
public interface DealCandidateRepository extends JpaRepository<DealCandidate, Long> {

    /**
     * 같은 마감 이벤트를 두 번 받아도 후보가 한 번만 생기게 하는 멱등 검사다. 순위 유니크
     * 제약이 없으므로 {@code findByIdForUpdate}로 물품 행을 잠근 뒤에 호출한다.
     */
    @Query("select count(dc) > 0 from DealCandidate dc "
            + "where dc.auctionItem.auctionItemId = :auctionItemId")
    boolean existsCandidate(@Param("auctionItemId") Long auctionItemId);

    /**
     * 낙찰됐는데 후보가 없는 물품을 찾는다. 마감 이벤트가 리스너까지 닿지 못해 후보 생성이
     * 빠진 물품이며, 복구 러너가 이 목록으로 {@code award}를 다시 부른다.
     *
     * <p><b>후보가 없는 것이 정상인 물품은 빼야 한다.</b> {@link #insertCandidatesFromBids}가
     * 탈퇴 회원을 걸러내므로 입찰자가 전원 탈퇴한 물품은 후보가 0건인 게 맞다. 그런 물품을
     * 담으면 복구가 돌 때마다 물품 행 락을 잡고 아무것도 하지 않는 헛일이 반복된다. 그래서
     * 삽입 쿼리와 <b>같은 조건</b>으로 "후보가 있어야 하는데 없는 물품"만 남긴다.
     *
     * <p>순서를 고정하는 것은 {@code findScheduleTargets}와 같은 이유다 — 복구 로그가 실행마다
     * 같은 순서로 남아야 재현할 수 있다.
     */
    default List<AwardRecoveryTargetProjection> findAwardRecoveryTargets() {
        return findAwardRecoveryTargets(AuctionItemStatus.SOLD);
    }

    @Query("select new com.hot6ix.upbid.domain.deal.repository.AwardRecoveryTargetProjection("
            + "  ai.auctionItemId, ai.auctionRoom.auctionRoomId) "
            + "from AuctionItem ai "
            + "where ai.status = :status "
            + "and not exists (select 1 from DealCandidate dc where dc.auctionItem = ai) "
            + "and exists (select 1 from Bid b join b.bidder u "
            + "            where b.auctionItem = ai and u.deletedAt is null) "
            + "order by ai.auctionItemId asc")
    List<AwardRecoveryTargetProjection> findAwardRecoveryTargets(
            @Param("status") AuctionItemStatus status);

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
     * @return 삽입된 행 수. <b>0이면 순위에 들 입찰이 없었다는 뜻</b>이다
     */
    default int insertCandidatesFromBids(Long auctionItemId) {
        return insertCandidatesFromBids(auctionItemId, DealCandidateStatus.WAITING.name());
    }

    @Modifying
    @Query(value = """
        INSERT INTO deal_candidates
            (auction_item_id, bidder_user_id, candidate_rank, bid_amount, status)
        SELECT :auctionItemId,
               t.bidder_user_id,
               ROW_NUMBER() OVER (ORDER BY t.amount DESC, t.bidder_user_id ASC),
               t.amount,
               :status
          FROM (SELECT bidder_user_id,
                       MAX(amount) AS amount
                  FROM bids
                 WHERE auction_item_id = :auctionItemId
                 GROUP BY bidder_user_id) t
          JOIN users u
            ON u.user_id = t.bidder_user_id
           AND u.deleted_at IS NULL
        """, nativeQuery = true)
    int insertCandidatesFromBids(@Param("auctionItemId") Long auctionItemId,
                                 @Param("status") String status);

    /**
     * 물품의 낙찰 후보를 순위대로 한 페이지 조회한다. 응답에 닉네임과 연락처가 필요하므로 입찰자를 fetch join한다.
     *
     * <p>화면이 "총 N명"과 페이지 번호를 그리고 구매자를 자기 순위가 있는 페이지로 보내므로
     * 전체 개수와 임의 페이지 접근이 필요하다. cursor로는 셋 다 낼 수 없어 offset으로 간다.
     *
     * <p>count 쿼리를 따로 준다. fetch join이 있는 쿼리에서 Spring Data가 만들어내는 count
     * 쿼리는 불필요한 조인을 남긴다.
     */
    @Query(value = "select dc from DealCandidate dc "
            + "join fetch dc.bidder b "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "and b.deletedAt is null "
            + "order by dc.candidateRank asc",
            countQuery = "select count(dc) from DealCandidate dc "
                    + "join dc.bidder b "
                    + "where dc.auctionItem.auctionItemId = :auctionItemId "
                    + "and b.deletedAt is null")
    Page<DealCandidate> findCandidates(@Param("auctionItemId") Long auctionItemId, Pageable pageable);

    /**
     * 요청자가 이 물품의 후보인지, 몇 위인지 확인한다. 목록이 페이지 단위라 목록만으로는
     * 알 수 없다 — 7위인 사람이 1페이지를 보고 있으면 자기 행이 그 안에 없다.
     * 한 사람은 한 물품에 후보 한 번만 오른다(입찰자별 최고가 한 행).
     */
    @Query("select dc from DealCandidate dc "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "and dc.bidder.userId = :bidderUserId")
    Optional<DealCandidate> findByBidder(@Param("auctionItemId") Long auctionItemId,
                                         @Param("bidderUserId") Long bidderUserId);

    /**
     * 요청자가 이 경매방의 물품들에서 각각 몇 위였는지 한 번에 조회한다. 결과 화면이 물품마다
     * "내 최종 순위"를 보여주는데, {@link #findByBidder}를 물품 수만큼 부르면 쿼리가 그만큼
     * 나간다.
     *
     * <p>후보로 오르지 않은 물품은 행이 없다. 없는 것과 0위는 다르므로 호출하는 쪽이
     * 물품 id로 찾아 쓰고, 없으면 순위를 비운다.
     *
     * @return 요청자가 후보인 물품만. 하나도 없으면 빈 목록
     */
    @Query("select new com.hot6ix.upbid.domain.deal.repository.MyCandidateRankProjection("
            + "  dc.auctionItem.auctionItemId, dc.candidateRank, dc.bidAmount) "
            + "from DealCandidate dc "
            + "where dc.auctionItem.auctionRoom.auctionRoomId = :auctionRoomId "
            + "and dc.bidder.userId = :bidderUserId")
    List<MyCandidateRankProjection> findMyRanksInRoom(@Param("auctionRoomId") Long auctionRoomId,
                                                      @Param("bidderUserId") Long bidderUserId);

    /**
     * 여러 물품의 후보를 한 번에 조회한다. 물품마다 조회하면 물품 수만큼 쿼리가 나간다.
     *
     * <p><b>탈퇴 회원도 걸러내지 않는다.</b> 이 목록으로 거래 상대와 후보 수를 동시에 계산하는데,
     * 상대에서는 탈퇴자를 빼고 후보 수에는 넣어야 한다. 필터를 SQL 에 걸면 두 값의 모집단이
     * 갈리므로 {@link com.hot6ix.upbid.domain.deal.entity.DealProgress} 가 자바에서 나눈다.
     *
     * <p>닉네임을 쓰므로 입찰자를 fetch join 한다. 정렬은 하지 않는다 — 순서를 정하는 규칙이
     * {@code DealProgress} 에 있고, 두 곳에 두면 갈라진다.
     */
    @Query("select dc from DealCandidate dc "
            + "join fetch dc.bidder "
            + "where dc.auctionItem.auctionItemId in :auctionItemIds")
    List<DealCandidate> findByAuctionItemIds(@Param("auctionItemIds") Collection<Long> auctionItemIds);

    /** 거래가 이미 끝났는지 판단한다. {@code COMPLETED} 후보가 있으면 더 바꿀 수 없다. */
    default boolean existsCompletedCandidate(Long auctionItemId) {
        return existsByStatus(auctionItemId, DealCandidateStatus.COMPLETED);
    }

    @Query("select count(dc) > 0 from DealCandidate dc "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "and dc.status = :status")
    boolean existsByStatus(@Param("auctionItemId") Long auctionItemId,
                           @Param("status") DealCandidateStatus status);

    /**
     * 현재 낙찰 권한자를 조회한다. {@code WAITING} 중 순위가 가장 낮은 1행이라는 정의를 쿼리로
     * 옮긴 것이다. 후보 전체를 읽어 메모리에서 고르면 후보 수만큼 비용이 늘어난다.
     */
    default Optional<DealCandidate> findCurrentWinner(Long auctionItemId) {
        return findFirstByStatus(auctionItemId, DealCandidateStatus.WAITING);
    }

    @Query("select dc from DealCandidate dc "
            + "join fetch dc.bidder b "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "and dc.status = :status "
            + "and b.deletedAt is null "
            + "order by dc.candidateRank asc limit 1")
    Optional<DealCandidate> findFirstByStatus(@Param("auctionItemId") Long auctionItemId,
                                              @Param("status") DealCandidateStatus status);

    /** 대상보다 앞 순번의 후보가 아직 기다리고 있으면, 지금은 그 후보의 차례다. */
    default boolean existsWaitingCandidateBefore(Long auctionItemId, Integer candidateRank) {
        return existsByStatusBeforeRank(auctionItemId, DealCandidateStatus.WAITING, candidateRank);
    }

    @Query("select count(dc) > 0 from DealCandidate dc "
            + "join dc.bidder b "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "and dc.status = :status "
            + "and b.deletedAt is null "
            + "and dc.candidateRank < :candidateRank")
    boolean existsByStatusBeforeRank(@Param("auctionItemId") Long auctionItemId,
                                     @Param("status") DealCandidateStatus status,
                                     @Param("candidateRank") Integer candidateRank);

    /** 실패한 후보 다음으로 차례가 넘어갈 후보를 찾는다. */
    default Optional<DealCandidate> findNextWaitingCandidate(Long auctionItemId, Integer candidateRank) {
        return findFirstByStatusAfterRank(auctionItemId, DealCandidateStatus.WAITING, candidateRank);
    }

    @Query("select dc from DealCandidate dc "
            + "join fetch dc.bidder b "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "and dc.status = :status "
            + "and b.deletedAt is null "
            + "and dc.candidateRank > :candidateRank "
            + "order by dc.candidateRank asc limit 1")
    Optional<DealCandidate> findFirstByStatusAfterRank(@Param("auctionItemId") Long auctionItemId,
                                                       @Param("status") DealCandidateStatus status,
                                                       @Param("candidateRank") Integer candidateRank);

    /**
     * 경로로 받은 물품에 속한 후보만 찾는다. 다른 물품의 후보 ID로는 조회되지 않는다.
     * 탈퇴한 회원의 후보도 조회되지 않는다 — 목록에 없는 후보를 ID로 찍어 처리할 수는 없다.
     */
    @Query("select dc from DealCandidate dc "
            + "join fetch dc.bidder b "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "and dc.dealCandidateId = :dealCandidateId "
            + "and b.deletedAt is null")
    Optional<DealCandidate> findCandidate(@Param("auctionItemId") Long auctionItemId,
                                          @Param("dealCandidateId") Long dealCandidateId);
}
