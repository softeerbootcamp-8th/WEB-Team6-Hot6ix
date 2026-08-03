package com.hot6ix.upbid.domain.auction.entity;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemStartRequestDto;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "auction_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_auction_items_product_id", columnNames = "product_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auction_item_id")
    private Long auctionItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_room_id", nullable = false)
    private AuctionRoom auctionRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_user_id")
    private User leaderUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "starting_price", nullable = false)
    private Long startingPrice;

    @Column(name = "bid_increment", nullable = false)
    private Long bidIncrement;

    @Column(name = "current_price", nullable = false)
    private Long currentPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AuctionItemStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "original_end_at")
    private LocalDateTime originalEndAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "total_extension_seconds", nullable = false)
    private Integer totalExtensionSeconds;

    @Builder
    private AuctionItem(AuctionRoom auctionRoom, User leaderUser, Product product, Long startingPrice,
                        Long bidIncrement, AuctionItemStatus status, LocalDateTime startedAt,
                        LocalDateTime originalEndAt, LocalDateTime endAt, Integer totalExtensionSeconds) {
        this.auctionRoom = auctionRoom;
        this.leaderUser = leaderUser;
        this.product = product;
        this.startingPrice = startingPrice;
        this.bidIncrement = bidIncrement;
        this.currentPrice = startingPrice;
        this.status = status != null ? status : AuctionItemStatus.READY;
        this.startedAt = startedAt;
        this.originalEndAt = originalEndAt;
        this.endAt = endAt;
        this.totalExtensionSeconds = totalExtensionSeconds != null ? totalExtensionSeconds : 0;
    }

    /**
     * 경매방에 올릴 대기(READY) 물품을 만든다. 입찰 단위는 요청이 아니라 <b>경매방 값을 복사</b>한다
     * — 한 방의 모든 물품이 같은 단위를 갖게 하려는 것이며, 복사한 뒤로는 방 값과 동기화되지 않는다.
     *
     * @param auctionRoom 물품을 올릴 경매방. 입찰 단위의 출처
     * @param product     올릴 상품
     * @param request     시작가를 담은 요청
     */
    public static AuctionItem from(AuctionRoom auctionRoom, Product product, AuctionItemAddRequestDto request) {
        return AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(product)
                .startingPrice(request.startingPrice())
                .bidIncrement(auctionRoom.getBidIncrement())
                .status(AuctionItemStatus.READY)
                .build();
    }

    /**
     * 대기 중인 물품의 경매를 시작한다. 상태·소유자 검증은 Service가 마치고 호출한다.
     *
     * <p>{@code endAt}은 {@code originalEndAt}과 같은 값에서 출발한다. 둘을 따로 두는 것은
     * 2주차 연장(Soft Close)이 붙었을 때 "원래 마감이 언제였는지"를 남겨두기 위해서이며,
     * 지금은 연장이 없어 두 값이 끝까지 같다. {@code totalExtensionSeconds}도 0 그대로다.
     *
     * @param request   경매 시간(분)을 담은 요청
     * @param startedAt 시작 시각. 마감 시각의 기준이라 Service가 정한 값을 받는다
     */
    public void start(AuctionItemStartRequestDto request, LocalDateTime startedAt) {
        this.status = AuctionItemStatus.IN_PROGRESS;
        this.startedAt = startedAt;
        this.originalEndAt = startedAt.plusMinutes(request.durationMinutes());
        this.endAt = this.originalEndAt;
    }

    /**
     * 진행 중인 경매를 마감한다. 최고 입찰자가 있으면 낙찰({@code SOLD}), 없으면
     * 유찰({@code FAILED})이다. 상태 검증은 Service가 마치고 호출한다.
     *
     * <p>{@code end_at}은 건드리지 않는다. 실제로 언제 닫혔는지가 아니라 <b>언제 닫히기로
     * 했는지</b>를 남기는 값이라, 마감이 락을 기다리다 늦게 실행돼도 그대로 둔다.
     */
    public void close() {
        this.status = leaderUser != null ? AuctionItemStatus.SOLD : AuctionItemStatus.FAILED;
    }

    /**
     * 입찰을 반영해 현재가와 최고 입찰자를 갱신한다.
     *
     * @param bidder 입찰자
     * @param amount 입찰 금액. Service에서 검증을 마친 값
     */
    public void applyBid(User bidder, Long amount) {
        this.leaderUser = bidder;
        this.currentPrice = amount;
    }
}
