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
import jakarta.persistence.Index;
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
@Table(
        name = "auction_items",
        indexes = @Index(name = "idx_auction_items_product_id", columnList = "product_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionItem extends BaseTimeEntity {

    /**
     * Soft Close 누적 연장 상한. 마감 직전 입찰이 계속 들어와도 한 물품이 원래 마감보다 1시간
     * 넘게 끌지 못하게 한다. 방마다 다르게 둘 값이 아니라 서비스 정책이라 상수로 둔다.
     */
    public static final int MAX_TOTAL_EXTENSION_SECONDS = 3600;

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

    /**
     * 마감이 임박한 상태였으면 Soft Close로 마감을 뒤로 밀고 누적 연장에 더한다. 연장 폭과
     * 임박 판정 기준은 이 물품이 속한 <b>경매방의 설정</b>이다.
     *
     * <p>연장이 일어나면 {@code endAt}이 바뀌므로, 호출한 쪽은 걸어둔 마감 예약을 새 시각으로
     * 갈아 끼워야 한다.
     *
     * <p><b>입찰 트랜잭션 안, 물품 행 락을 잡은 채로 불러야 한다.</b> 입찰 커밋과 마감 시각
     * 갱신이 갈라지면 그 사이에 마감이 끼어들어, 입찰은 성공했는데 연장은 없던 일이 되는
     * 구간이 생긴다.
     *
     * <p>연장하지 않는 경우가 셋이다. 경매방에 Soft Close 설정이 없거나(값이 {@code null}),
     * 아직 임박 구간에 들어오지 않았거나, 이번 연장까지 더하면 누적이
     * {@link #MAX_TOTAL_EXTENSION_SECONDS}를 넘는 경우다. 상한을 넘길 때 남은 만큼만 밀지
     * 않는 것은 연장 폭이 언제나 방 설정값과 같아야 화면에 알리는 "몇 초 연장"과 실제 마감
     * 시각이 어긋나지 않기 때문이다.
     *
     * @param now 판정 기준 시각. 같은 트랜잭션의 다른 검증과 <b>같은 값</b>을 받아야 한다
     * @return 연장했으면 {@code true}. {@code false}면 아무 값도 바뀌지 않았다
     */
    public boolean extendIfClosingSoon(LocalDateTime now) {

        Integer triggerSeconds = auctionRoom.getSoftCloseTriggerSeconds();
        Integer extendSeconds = auctionRoom.getSoftCloseExtendSeconds();

        if (endAt == null || triggerSeconds == null || extendSeconds == null) {
            return false;
        }

        if (now.isBefore(endAt.minusSeconds(triggerSeconds))) {
            return false;
        }

        if (totalExtensionSeconds + extendSeconds > MAX_TOTAL_EXTENSION_SECONDS) {
            return false;
        }

        this.endAt = endAt.plusSeconds(extendSeconds);
        this.totalExtensionSeconds += extendSeconds;

        return true;
    }
}
