package com.hot6ix.upbid.domain.deal.entity;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.common.BaseTimeEntity;
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

@Getter
@Entity
@Table(name = "deal_candidates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DealCandidate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_item_id")
    private AuctionItem auctionItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_user_id")
    private User bidder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private DealCandidateStatus status;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Builder
    private DealCandidate(AuctionItem auctionItem, User bidder, DealCandidateStatus status,
                          LocalDateTime completedAt, LocalDateTime failedAt) {
        this.auctionItem = auctionItem;
        this.bidder = bidder;
        this.status = status != null ? status : DealCandidateStatus.WAITING;
        this.completedAt = completedAt;
        this.failedAt = failedAt;
    }
}
