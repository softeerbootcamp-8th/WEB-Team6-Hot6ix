package com.hot6ix.upbid.domain.auction.entity;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.global.common.BaseEntity;
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
public class AuctionRoom extends BaseEntity {

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

    @Column(name = "share_code", length = 32, unique = true)
    private String shareCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private AuctionRoomStatus status;

    /**
     * 이 방의 모든 물품이 공유하는 입찰 단위. 물품을 추가할 때 이 값을 물품으로 복사한다.
     * 입찰 검증은 복사된 물품 값을 읽으므로, 생성 이후에는 바꾸지 않는다.
     */
    @Column(name = "bid_increment", nullable = false)
    private Long bidIncrement;

    @Column(name = "soft_close_trigger_seconds")
    private Integer softCloseTriggerSeconds;

    @Column(name = "soft_close_extend_seconds")
    private Integer softCloseExtendSeconds;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Builder
    private AuctionRoom(SellerProfile sellerProfile, String name, String coverImageUrl, String description,
                        String liveUrl, String shareCode, AuctionRoomStatus status, Long bidIncrement,
                        Integer softCloseTriggerSeconds, Integer softCloseExtendSeconds, LocalDateTime closedAt) {
        this.sellerProfile = sellerProfile;
        this.name = name;
        this.coverImageUrl = coverImageUrl;
        this.description = description;
        this.liveUrl = liveUrl;
        this.shareCode = shareCode;
        this.status = status != null ? status : AuctionRoomStatus.BEFORE;
        this.bidIncrement = bidIncrement;
        this.softCloseTriggerSeconds = softCloseTriggerSeconds;
        this.softCloseExtendSeconds = softCloseExtendSeconds;
        this.closedAt = closedAt;
    }

    public static AuctionRoom from(SellerProfile sellerProfile, AuctionRoomCreateRequestDto request, String shareCode) {
        return AuctionRoom.builder()
                .sellerProfile(sellerProfile)
                .name(request.name())
                .coverImageUrl(request.coverImageUrl())
                .description(request.description())
                .liveUrl(request.liveUrl())
                .shareCode(shareCode)
                .bidIncrement(request.bidIncrement())
                .softCloseTriggerSeconds(request.softCloseTriggerSeconds())
                .softCloseExtendSeconds(request.softCloseExtendSeconds())
                .build();
    }

    /**
     * 요청에서 값이 온 필드만 부분 병합한다. 생략된(null) 필드는 기존 값을 그대로 유지한다.
     */
    public void update(AuctionRoomUpdateRequestDto request) {
        if (request.name() != null) {
            this.name = request.name();
        }
        if (request.coverImageUrl() != null) {
            this.coverImageUrl = request.coverImageUrl();
        }
        if (request.description() != null) {
            this.description = request.description();
        }
        if (request.liveUrl() != null) {
            this.liveUrl = request.liveUrl();
        }
        if (request.softCloseTriggerSeconds() != null) {
            this.softCloseTriggerSeconds = request.softCloseTriggerSeconds();
        }
        if (request.softCloseExtendSeconds() != null) {
            this.softCloseExtendSeconds = request.softCloseExtendSeconds();
        }
    }
}
