package com.hot6ix.upbid.domain.auction.entity;

import com.hot6ix.upbid.domain.product.entity.Product;
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
@Table(name = "auction_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_room_id")
    private AuctionRoom auctionRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_user_id")
    private User leaderUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "starting_price")
    private Long startingPrice;

    @Column(name = "bid_increment")
    private Long bidIncrement;

    @Column(name = "current_price")
    private Long currentPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private AuctionItemStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "original_end_at")
    private LocalDateTime originalEndAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "total_extension_seconds")
    private Integer totalExtensionSeconds;

    @Builder
    private AuctionItem(AuctionRoom auctionRoom, User leaderUser, Product product, Long startingPrice,
                        Long bidIncrement, Long currentPrice, AuctionItemStatus status, LocalDateTime startedAt,
                        LocalDateTime originalEndAt, LocalDateTime endAt, Integer totalExtensionSeconds) {
        this.auctionRoom = auctionRoom;
        this.leaderUser = leaderUser;
        this.product = product;
        this.startingPrice = startingPrice;
        this.bidIncrement = bidIncrement;
        this.currentPrice = currentPrice;
        this.status = status != null ? status : AuctionItemStatus.READY;
        this.startedAt = startedAt;
        this.originalEndAt = originalEndAt;
        this.endAt = endAt;
        this.totalExtensionSeconds = totalExtensionSeconds != null ? totalExtensionSeconds : 0;
    }
}
