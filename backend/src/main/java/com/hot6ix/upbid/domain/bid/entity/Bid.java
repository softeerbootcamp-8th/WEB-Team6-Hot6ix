package com.hot6ix.upbid.domain.bid.entity;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 한 물품에서 같은 금액은 한 번만 입찰될 수 있다. 현재가 이하를 거절하는 규칙에서는 같은
 * 금액이 정당한 경우가 없고, 이 제약이 있으면 금액 순서가 곧 시각 순서가 되어 낙찰 순위를
 * 금액만으로 결정할 수 있다.
 *
 * <p>제약은 값의 중복만 막는다. 서로 다른 금액이 현재가 검사를 통과한 뒤 뒤바뀐 순서로
 * 커밋되는 것은 막지 못하므로, 입찰 동시성은 물품 행 잠금으로 따로 다뤄야 한다.
 *
 * <p>{@code idx_bids_item_bidder_amount}는 리더보드 조회({@code findTopBidders})를 위한
 * 것이다. 그 쿼리는 물품과 입찰자로 묶어 최고가를 뽑는데, {@code uk_bids_item_amount}는
 * {@code bidder_user_id}를 담지 않아 테이블 본체를 다시 읽어야 한다. 세 컬럼을 순서대로
 * 담으면 인덱스만 읽고 끝난다.
 */
@Getter
@Entity
@Table(
        name = "bids",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_bids_item_amount",
                        columnNames = {"auction_item_id", "amount"}),
                @UniqueConstraint(
                        name = "uk_bids_request_id",
                        columnNames = "request_id")
        },
        indexes = @Index(
                name = "idx_bids_item_bidder_amount",
                columnList = "auction_item_id, bidder_user_id, amount")
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bid_id")
    private Long bidId;

    /**
     * Redis가 승인한 요청을 MySQL에 한 번만 반영하기 위한 멱등 키.
     *
     * <p>기존 동기 입찰 행과 롤링 배포를 허용하기 위해 이번 migration에서는 nullable이다.
     * Redis Stream 경로로 생성하는 입찰은 반드시 값을 채운다.
     */
    @Column(name = "request_id", length = 64)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_item_id", nullable = false)
    private AuctionItem auctionItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_user_id", nullable = false)
    private User bidder;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @CreatedDate
    @Column(name = "accepted_at", updatable = false, nullable = false)
    private LocalDateTime acceptedAt;

    @Builder
    private Bid(String requestId, AuctionItem auctionItem, User bidder, Long amount) {
        this.requestId = requestId;
        this.auctionItem = auctionItem;
        this.bidder = bidder;
        this.amount = amount;
    }
}
