package com.hot6ix.upbid.domain.deal.entity;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 마감 시점에 확정한 낙찰 후보 스냅샷. 거래가 깨지면 차순위로 권한을 넘긴다.
 * <b>현재 낙찰 권한자 = {@code WAITING} 후보 중 {@code candidateRank}가 가장 낮은 1행</b>
 * 으로 정의하므로 별도 플래그나 승격 UPDATE가 없다. {@code COMPLETED} 후보가 있으면 거래가
 * 끝난 것이므로 남은 {@code WAITING}은 무시한다. 순위 중복은 물품 행 비관적 락이 막는다.
 *
 * <p>{@code BaseTimeEntity}를 상속하지 않는다. 상태 전이가 한 번뿐이고 그 시각을
 * {@code completedAt}·{@code failedAt}이 기록하므로 수정 시각에 담을 정보가 없고, 생성 시각도
 * 마감 시각({@code auction_items.end_at})과 사실상 같다.
 */
@Getter
@Entity
@Table(name = "deal_candidates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DealCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deal_candidate_id", nullable = false)
    private Long dealCandidateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_item_id", nullable = false)
    private AuctionItem auctionItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_user_id", nullable = false)
    private User bidder;

    /**
     * 낙찰 순위. 1이 최초 낙찰자다. {@code rank}는 MySQL 8.0+ 예약어라 컬럼명을 바꿔 썼다.
     */
    @Column(name = "candidate_rank", nullable = false)
    private Integer candidateRank;

    /**
     * 이 후보의 최고 입찰가 스냅샷. 차순위로 권한이 넘어가면 이 값이 그대로 새 낙찰가가 되므로,
     * 이전할 때마다 {@code bids}를 다시 조회하지 않아도 된다.
     */
    @Column(name = "bid_amount", nullable = false)
    private Long bidAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private DealCandidateStatus status;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Builder
    private DealCandidate(AuctionItem auctionItem, User bidder, Integer candidateRank, Long bidAmount,
                          DealCandidateStatus status, LocalDateTime completedAt, LocalDateTime failedAt) {
        this.auctionItem = auctionItem;
        this.bidder = bidder;
        this.candidateRank = candidateRank;
        this.bidAmount = bidAmount;
        this.status = status != null ? status : DealCandidateStatus.WAITING;
        this.completedAt = completedAt;
        this.failedAt = failedAt;
    }

    /**
     * 거래 성사를 확정한다. 호출 가능한 상태인지({@code WAITING}), 요청자가 판매자인지는
     * Service에서 검증한다.
     *
     * @param completedAt 성사 확정 시각
     */
    public void complete(LocalDateTime completedAt) {
        this.status = DealCandidateStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    /**
     * 거래 실패를 기록한다. 이 후보가 빠지면 차순위가 자동으로 현재 낙찰자가 되므로
     * 여기서 다음 후보를 손대지 않는다.
     *
     * @param failedAt 실패 확정 시각
     */
    public void fail(LocalDateTime failedAt) {
        this.status = DealCandidateStatus.FAILED;
        this.failedAt = failedAt;
    }
}
