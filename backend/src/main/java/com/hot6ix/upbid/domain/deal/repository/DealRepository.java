package com.hot6ix.upbid.domain.deal.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 거래 내역 조회 전용. {@link DealCandidateRepository}와 나눈 이유는 다루는 대상이 달라서다 —
 * 여기 쿼리는 후보 한 명이 아니라 <b>내가 관여한 거래</b>를 찾고, 그러려면 판매 쪽
 * ({@code auction_items})과 구매 쪽({@code deal_candidates})을 함께 봐야 한다.
 */
public interface DealRepository extends Repository<DealCandidate, Long> {

    /**
     * 거래 내역 조회 상한. 화면에 페이지네이션이 없어 전량을 내리므로 여기서만 크기를 막는다.
     * 거래가 이보다 많은 회원이 생기면 오래된 것부터 조용히 잘리므로, 그때는 상한을 올릴 게
     * 아니라 화면과 함께 페이지네이션을 도입해야 한다.
     */
    int MAX_DEAL_SIZE = 100;

    default List<DealSummaryProjection> findDeals(Long userId) {
        return findDeals(userId,
                AuctionItemStatus.SOLD.name(),
                AuctionItemStatus.FAILED.name(),
                DealCandidateStatus.COMPLETED.name(),
                DealCandidateStatus.WAITING.name(),
                DealCandidateStatus.FAILED.name(),
                MAX_DEAL_SIZE);
    }

    /**
     * 내가 판 것과 산 것을 한 목록으로 모은다. 원천이 둘이라 UNION이 필요하고, JPQL은 UNION을
     * 지원하지 않아 네이티브로 간다.
     *
     * <p><b>판매 쪽</b>은 내 경매방의 마감된 물품이다. 낙찰(SOLD)과 유찰(FAILED)만 들어오고
     * 진행 중이거나 대기 중인 물품은 아직 거래가 아니다.
     *
     * <p><b>구매 쪽</b>은 내가 후보로 오른 물품이다. 후보가 됐다는 건 유효한 입찰을 했다는
     * 뜻이라 별도 상태 조건이 없다. 후보가 있으면 유찰일 수 없어 구매 건에는 UNSOLD가 없다.
     *
     * <p>거래 상대는 성사된 후보가 있으면 그 사람, 없으면 순서를 기다리는 최저 순위 후보다.
     * 실패한 후보는 이미 지나간 상대라 제외한다. 전원 실패하면 상대가 {@code null}이 된다.
     *
     * <p><b>상대가 사라져도 내 거래 내역은 남는다.</b> 판매자가 탈퇴하거나 경매방·상품·판매자
     * 프로필을 지워도 구매자의 내역에서 그 거래가 빠지지 않는다. 거래 내역은 지금의 상태가
     * 아니라 지나간 사실이라, 상대가 나갔다고 내 기록이 없어지면 안 된다. 그래서 경로에 놓인
     * 조인에는 {@code deleted_at} 조건을 걸지 않는다.
     *
     * <p>거래 상대를 고를 때만 예외로 탈퇴 회원을 뺀다. 탈퇴한 회원은 낙찰 후보로 취급하지
     * 않으므로({@link DealCandidateRepository}) 순위가 다음 후보에게 넘어가 있고, 내역에도
     * 그 다음 후보가 상대로 나와야 화면과 어긋나지 않는다.
     *
     * <p>정렬 키를 셋까지 두는 이유는 순서가 하나로 정해지게 하기 위해서다. 같은 시각에 마감된
     * 물품이 흔하고, 그때 순서가 흔들리면 화면이 요청마다 다르게 보인다.
     *
     * <p>UNION 결과를 파생 테이블로 감싸고 각 {@code SELECT}를 괄호로 묶는다. 정렬과 상한이
     * 합쳐진 전체에 걸린다는 것이 문장 모양으로 드러나야, 읽는 사람이 뒤쪽 {@code SELECT}에만
     * 걸린 것으로 오해하지 않는다.
     */
    @Query(value = """
            SELECT * FROM (
            (SELECT 1                   AS sellerRow,
                   ai.auction_item_id   AS auctionItemId,
                   ar.auction_room_id   AS auctionRoomId,
                   ar.share_code        AS shareCode,
                   p.product_id         AS productId,
                   p.name               AS productName,
                   p.image_url          AS imageUrl,
                   ar.name              AS auctionRoomName,
                   ai.status            AS itemStatus,
                   EXISTS (SELECT 1 FROM deal_candidates c
                            WHERE c.auction_item_id = ai.auction_item_id
                              AND c.status = :completedStatus) AS dealCompleted,
                   EXISTS (SELECT 1 FROM deal_candidates c
                            WHERE c.auction_item_id = ai.auction_item_id
                              AND c.status = :waitingStatus) AS hasWaitingCandidate,
                   NULL                 AS myCandidateStatus,
                   0                    AS myTurn,
                   ai.current_price     AS amount,
                   (SELECT bu.nickname
                      FROM deal_candidates c
                      JOIN users bu ON bu.user_id = c.bidder_user_id
                                   AND bu.deleted_at IS NULL
                     WHERE c.auction_item_id = ai.auction_item_id
                       AND c.status <> :failedCandidateStatus
                     ORDER BY CASE WHEN c.status = :completedStatus THEN 0 ELSE 1 END,
                              c.candidate_rank
                     LIMIT 1)           AS partnerNickname,
                   sp.seller_profile_id AS sellerProfileId,
                   ai.end_at            AS closedAt
              FROM auction_items ai
              JOIN auction_rooms ar    ON ar.auction_room_id = ai.auction_room_id
              JOIN seller_profiles sp  ON sp.seller_profile_id = ar.seller_profile_id
              JOIN products p          ON p.product_id = ai.product_id
             WHERE sp.user_id = :userId
               AND ai.status IN (:soldStatus, :failedStatus))
            UNION ALL
            (SELECT 0                   AS sellerRow,
                   ai.auction_item_id   AS auctionItemId,
                   ar.auction_room_id   AS auctionRoomId,
                   ar.share_code        AS shareCode,
                   NULL                 AS productId,
                   p.name               AS productName,
                   p.image_url          AS imageUrl,
                   ar.name              AS auctionRoomName,
                   ai.status            AS itemStatus,
                   EXISTS (SELECT 1 FROM deal_candidates c
                            WHERE c.auction_item_id = ai.auction_item_id
                              AND c.status = :completedStatus) AS dealCompleted,
                   EXISTS (SELECT 1 FROM deal_candidates c
                            WHERE c.auction_item_id = ai.auction_item_id
                              AND c.status = :waitingStatus) AS hasWaitingCandidate,
                   dc.status            AS myCandidateStatus,
                   NOT EXISTS (
                       SELECT 1 FROM deal_candidates o
                         JOIN users ou ON ou.user_id = o.bidder_user_id AND ou.deleted_at IS NULL
                        WHERE o.auction_item_id = dc.auction_item_id
                          AND o.status = :waitingStatus
                          AND (o.candidate_rank < dc.candidate_rank
                               OR (o.candidate_rank = dc.candidate_rank
                                   AND o.deal_candidate_id < dc.deal_candidate_id))
                   )                    AS myTurn,
                   dc.bid_amount        AS amount,
                   su.nickname          AS partnerNickname,
                   sp.seller_profile_id AS sellerProfileId,
                   ai.end_at            AS closedAt
              FROM deal_candidates dc
              JOIN auction_items ai    ON ai.auction_item_id = dc.auction_item_id
              JOIN auction_rooms ar    ON ar.auction_room_id = ai.auction_room_id
              JOIN seller_profiles sp  ON sp.seller_profile_id = ar.seller_profile_id
              JOIN users su            ON su.user_id = sp.user_id
              JOIN products p          ON p.product_id = ai.product_id
             WHERE dc.bidder_user_id = :userId)
            ) deals
             ORDER BY closedAt DESC, auctionItemId DESC, sellerRow DESC
             LIMIT :size
            """, nativeQuery = true)
    List<DealSummaryProjection> findDeals(@Param("userId") Long userId,
                                          @Param("soldStatus") String soldStatus,
                                          @Param("failedStatus") String failedStatus,
                                          @Param("completedStatus") String completedStatus,
                                          @Param("waitingStatus") String waitingStatus,
                                          @Param("failedCandidateStatus") String failedCandidateStatus,
                                          @Param("size") int size);
}
