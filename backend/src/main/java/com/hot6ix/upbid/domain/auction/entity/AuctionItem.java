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

    /**
     * 경매방에 Soft Close 트리거가 없을 때 {@link #applyCloseAdvanced}가 대신 쓰는 값. 경매방
     * 생성 요청에서 트리거는 필수이고 최소 60초라 정상 경로로는 비어 있을 수 없지만, 컬럼이
     * nullable이라 방어값이 필요하다. 생성 API가 허용하는 최소값과 같은 값을 쓴다 — 다른
     * 값을 두면 화면이 만들 수 없는 마감 시각이 이 경로에서만 생긴다.
     *
     * <p><b>{@code close-auction.lua}에도 같은 값이 박혀 있다.</b> 앞당기기 판정은 거기서 하고
     * 여기서는 그 결과를 받아 적으므로, 한쪽만 바꾸면 알림 기록 여부가 어긋난다.
     */
    public static final int DEFAULT_SOFT_CLOSE_TRIGGER_SECONDS = 60;

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

    /**
     * 마감 임박 알림이 마지막으로 나간 시각. 아직 안 나갔으면 {@code null}이다.
     *
     * <p><b>알림을 실제로 발행하는 경로에서는 여기서 값을 바꾸지 않는다.</b>
     * {@code AuctionItemRepository.markNotified}가 조건부 UPDATE 한 문장으로 고친다. 엔티티를
     * 읽어 고치면 읽기와 쓰기 사이가 벌어져 두 서버가 같이 통과하기 때문이다.
     *
     * <p>{@link #applyCloseAdvanced}만 예외다. 그쪽은 Redis가 이미 확정한 앞당김을 <b>물품 행
     * 락을 잡은 채</b> 받아 적는 자리라 그 틈이 없다.
     */
    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

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
     * 이 물품이 속한 경매방의 Soft Close 트리거 초. 값이 없으면
     * {@link #DEFAULT_SOFT_CLOSE_TRIGGER_SECONDS}다.
     *
     * <p>{@link #extendIfClosingSoon}은 이 기본값을 쓰지 않고 설정이 없으면 연장하지 않는다.
     * 연장은 마감을 뒤로 미는 일이라 설정하지 않은 방에서 임의로 일어나면 안 되지만, 앞당기기는
     * 판매자가 직접 누른 조작이라 기준이 없다고 거절할 이유가 없다.
     */
    private int resolveSoftCloseTriggerSeconds() {
        Integer triggerSeconds = auctionRoom.getSoftCloseTriggerSeconds();
        return triggerSeconds != null ? triggerSeconds : DEFAULT_SOFT_CLOSE_TRIGGER_SECONDS;
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
     * Redis에서 이미 승인된 입찰을 MySQL의 조회용 상태에 반영한다.
     *
     * <p>Stream의 순서와 MySQL 트랜잭션 커밋 순서는 같다는 보장이 없다. 따라서 더 낮은
     * 금액, 더 이른 마감 시각, 더 작은 누적 연장값은 기존 상태를 덮지 않는다. 판매자가
     * 마감을 앞당긴 뒤 그보다 전에 승인된 입찰 이벤트가 늦게 도착한 경우에는 과거
     * {@code endAt}으로 앞당김을 취소하지 않는다. 누적 연장값은 과거에 실제로 일어난
     * 연장 횟수이므로 이 경우에도 큰 값이면 보존한다.
     *
     * @return 이 이벤트로 {@code endAt}이 실제로 뒤로 이동했으면 {@code true}
     */
    public boolean applyPersistedBid(User bidder, Long amount, LocalDateTime acceptedAt,
                                     LocalDateTime persistedEndAt,
                                     int persistedTotalExtensionSeconds) {

        if (leaderUser == null || amount > currentPrice) {
            applyBid(bidder, amount);
        }

        boolean acceptedAfterCloseAdvanced =
                notifiedAt == null || acceptedAt.isAfter(notifiedAt);
        boolean endAtAdvanced = acceptedAfterCloseAdvanced
                && (endAt == null || persistedEndAt.isAfter(endAt));
        if (endAtAdvanced) {
            endAt = persistedEndAt;
        }

        if (persistedTotalExtensionSeconds > totalExtensionSeconds) {
            totalExtensionSeconds = persistedTotalExtensionSeconds;
        }

        return endAtAdvanced;
    }

    /**
     * 같은 Stream에서 앞선 입찰을 모두 반영한 뒤 도착한 마감 스냅샷으로 최종 상태를 맞춘다.
     * Redis가 경매 중 판정 원본이므로 마감 순간의 최고가·최고 입찰자를 그대로 사용한다.
     */
    public void closeFromRedis(User leader, Long finalPrice, LocalDateTime finalEndAt,
                               int finalTotalExtensionSeconds) {
        this.leaderUser = leader;
        this.currentPrice = finalPrice;
        this.endAt = finalEndAt;
        this.totalExtensionSeconds = finalTotalExtensionSeconds;
        close();
    }

    /**
     * Redis에서 원자적으로 확정한 판매자 마감 앞당기기를 DB에 한 번만 반영한다.
     *
     * <p><b>재전달 판정을 {@code end_at} 비교로 한다.</b> 앞당기기는 마감을 <b>앞으로만</b>
     * 옮기는 유일한 경로라, 들어온 시각이 지금 값보다 앞이 아니면 같은 이벤트가 다시 왔거나
     * 더 나중 앞당김에 이미 밀린 낡은 이벤트다. 둘 다 반영하면 안 된다.
     *
     * <p>예전에는 이 판정을 {@code notifiedAt}으로 했는데, 아래에서 그 값을 <b>조건부로</b>
     * 찍게 되면서 겸용할 수 없게 됐다. 알림을 안 찍는 경우에도 재전달은 걸러야 한다.
     *
     * <p><b>{@code notifiedAt}은 딱 트리거만큼 남길 때만 찍는다.</b> 그때만 앞당긴 뒤의 알림
     * 시각({@code endAt - 트리거})이 정확히 지금이라 이미 지난 시각이고, 남은 초는 앞당김
     * 이벤트가 화면에 직접 알리므로 알림이 따로 나갈 이유가 없다. 안 찍으면
     * {@code AuctionRecoveryRunner}가 "알림 예약이 빠졌다"고 보고 되살려서, 트리거만큼 남은
     * 물품에 "마감 N초 전"이 뒤늦게 나간다(#290). 반대로 그보다 길게 남기면 알림 시각이 아직
     * 미래라 <b>알림이 나가야 하므로 찍으면 안 된다</b>(#336).
     *
     * @param advancedEndAt    앞당겨진 마감 시각
     * @param advancedAt       Redis가 앞당김을 확정한 시각
     * @param remainingSeconds 그 시각까지 남긴 초. 트리거와 같은지로 알림 기록 여부가 갈린다
     * @return 처음 반영했으면 {@code true}, 재전달이거나 낡은 이벤트면 {@code false}
     */
    public boolean applyCloseAdvanced(
            LocalDateTime advancedEndAt, LocalDateTime advancedAt, int remainingSeconds) {

        if (endAt == null || !advancedEndAt.isBefore(endAt)) {
            return false;
        }

        this.endAt = advancedEndAt;

        if (remainingSeconds == resolveSoftCloseTriggerSeconds()) {
            this.notifiedAt = advancedAt;
        }

        return true;
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
