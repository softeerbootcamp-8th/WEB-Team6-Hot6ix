package com.hot6ix.upbid.domain.auction.entity;

import com.hot6ix.upbid.domain.user.entity.SellerProfile;
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
@Table(name = "auction_rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auction_room_id")
    private Long auctionRoomId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_profile_id")
    private SellerProfile sellerProfile;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "live_url", columnDefinition = "TEXT")
    private String liveUrl;

    @Column(name = "share_code", length = 32)
    private String shareCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private AuctionRoomStatus status;

    @Column(name = "soft_close_trigger_seconds")
    private Integer softCloseTriggerSeconds;

    @Column(name = "soft_close_extend_seconds")
    private Integer softCloseExtendSeconds;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Builder
    private AuctionRoom(SellerProfile sellerProfile, String name, String coverImageUrl, String description,
                        String liveUrl, String shareCode, AuctionRoomStatus status,
                        Integer softCloseTriggerSeconds, Integer softCloseExtendSeconds, LocalDateTime closedAt) {
        this.sellerProfile = sellerProfile;
        this.name = name;
        this.coverImageUrl = coverImageUrl;
        this.description = description;
        this.liveUrl = liveUrl;
        this.shareCode = shareCode;
        this.status = status != null ? status : AuctionRoomStatus.BEFORE;
        this.softCloseTriggerSeconds = softCloseTriggerSeconds;
        this.softCloseExtendSeconds = softCloseExtendSeconds;
        this.closedAt = closedAt;
    }
}
