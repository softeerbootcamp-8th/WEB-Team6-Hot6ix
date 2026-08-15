package com.hot6ix.upbid.domain.bid.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.bid.dto.response.BidCreateResponseDto;
import com.hot6ix.upbid.domain.bid.entity.Bid;
import com.hot6ix.upbid.domain.bid.exception.BidErrorType;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
import lombok.RequiredArgsConstructor;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

    private final BidRepository bidRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;
    /** 계측은 이 객체가 안다. 서비스는 "무엇을 잰다"만 알고 Micrometer 는 모른다. */
    private final BidMetrics bidMetrics;

    /**
     * 입찰을 접수해 기록을 남기고 물품의 현재가·최고 입찰자를 갱신한다.
     *
     * <p>물품 행에 쓰기 락을 걸고 읽으므로 같은 물품에 대한 입찰이 한 줄로 직렬화된다.
     * 자기 차례에 최신 현재가를 읽기 때문에, 경합에 밀렸어도 금액이 여전히 유효하면 그대로
     * 통과한다. 락을 잡기 전에 끝낼 수 있는 입찰자 조회는 먼저 해서 락 유지 시간을 줄인다.
     *
     * <p>판매자 본인인지와 이 방의 참여자인지는 물품 상태와 무관해 락 없이 판정할 수 있다.
     * 락 앞에서 걸러 자격 없는 요청이 물품 행 락을 잡지 않게 한다({@link #validateEntitled}).
     *
     * <p>마감이 임박한 입찰이면 <b>같은 락 안에서</b> Soft Close 연장까지 마친다. 연장을 커밋
     * 뒤로 미루면 입찰은 이미 저장됐는데 마감 시각은 아직 그대로인 구간이 생기고, 하필 그때
     * 마감 예약이 깨면 방금 받은 입찰을 두고 물품이 닫힌다.
     *
     * <p><b>시각을 둘 쓴다.</b> 입찰을 받을지와 마감을 연장할지는 서로 다른 질문이라 기준도
     * 다르다. 하나로 합치면 어느 쪽으로 맞추든 다른 쪽이 손해를 본다.
     *
     * <ul>
     *   <li>{@code arrivedAt}(락 앞) &mdash; 받아줄지 판정한다. 마감 전에 보낸 입찰이
     *       <b>락을 기다렸다는 이유로</b> 거절되면 안 된다. 앞사람이 같은 물품에 입찰 중이라
     *       줄을 섰을 뿐인데, 그 대기가 길수록 불리해지는 것은 보낸 사람 책임이 아니다.</li>
     *   <li>{@code now}(락 뒤) &mdash; 연장할지 판정한다. 연장의 목적은 남은 사람에게 반응할
     *       시간을 주는 것이라 <b>지금</b> 임박했는지로 재야 한다. 낡은 시각으로 보면 실제로는
     *       임박 구간에 들어왔는데 아직 아니라고 판단해 연장을 조용히 건너뛴다.</li>
     * </ul>
     *
     * <p>락 대기가 길어 {@code arrivedAt}이 한참 낡았더라도 그 사이에 마감이 실제로 실행됐다면
     * 상태 검사가 먼저 걸러낸다. 시각만으로 무한정 관대해지지는 않는다.
     *
     * @param auctionItemId 입찰할 물품의 ID
     * @param bidderUserId  입찰자의 회원 ID
     * @param amount        입찰 금액
     * @return 접수된 입찰
     * @throws ApplicationException 입찰자가 없거나(RESOURCE_NOT_FOUND), 물품이 없거나
     *                              (AUCTION_ITEM_NOT_FOUND), 자격이 없거나 거절 조건에
     *                              걸렸을 때(7xxx)
     */
    @Transactional
    public BidCreateResponseDto place(Long auctionItemId, Long bidderUserId, Long amount) {

        long enteredAt = System.nanoTime();
        LocalDateTime arrivedAt = LocalDateTime.now(clock);

        User bidder = userRepository.findByUserIdAndDeletedAtIsNull(bidderUserId)
                .orElseThrow(() -> new ApplicationException(CommonErrorType.RESOURCE_NOT_FOUND));

        Long sellerUserId = auctionItemRepository.findSellerUserId(auctionItemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        validateEntitled(auctionItemId, bidder, sellerUserId);

        bidMetrics.recordBeforeLock(enteredAt);

        // 락 시작. 여기서 기다린 시간을 잰다 (자세한 이유는 BidMetrics 참고).
        AuctionItem auctionItem = bidMetrics.recordLockWait(() ->
                auctionItemRepository.findByIdForUpdate(auctionItemId)
                        .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND)));

        LocalDateTime now = LocalDateTime.now(clock);

        validateBiddable(auctionItem, bidder, arrivedAt);
        validateAmount(auctionItem, amount);

        Bid bid = saveBid(auctionItem, bidder, amount);
        auctionItem.applyBid(bidder, amount);

        // 리스너가 커밋 후에만 받으므로(DomainEventSseListener) 여기서 발행해도 롤백되면 나가지 않는다.
        domainEventPublisher.publish(BidPlaced.of(
                auctionItem.getAuctionRoom().getAuctionRoomId(),
                auctionItem.getAuctionItemId(),
                auctionItem.getProduct().getName(),
                bidder.getNickname(),
                amount,
                bid.getAcceptedAt()));

        publishIfExtended(auctionItem, now);

        return BidCreateResponseDto.from(bid);
    }

    /**
     * 마감이 임박했으면 연장하고 그 사실을 알린다. 연장이 없으면 아무 일도 하지 않는다.
     *
     * <p>{@code BidPlaced} 다음에 발행해 화면 이벤트 피드에 "입찰 발생 → 연장" 순서로 쌓이게
     * 한다. 연장이 먼저 보이면 무엇 때문에 밀렸는지 알 수 없다.
     *
     * <p>이 이벤트는 {@code AuctionCloseScheduler}가 마감 예약을 갈아 끼우는 신호이기도 하다.
     *
     * @param now 락을 잡은 뒤에 구한 현재 시각. 받아들일지 판정한 {@code arrivedAt}과 달리
     *            <b>지금</b>을 기준으로 임박 여부를 봐야 연장을 놓치지 않는다
     */
    private void publishIfExtended(AuctionItem auctionItem, LocalDateTime now) {

        if (!auctionItem.extendIfClosingSoon(now)) {
            return;
        }

        domainEventPublisher.publish(SoftCloseExtended.of(
                auctionItem.getAuctionRoom().getAuctionRoomId(),
                auctionItem.getAuctionItemId(),
                auctionItem.getProduct().getName(),
                auctionItem.getAuctionRoom().getSoftCloseExtendSeconds(),
                auctionItem.getEndAt(),
                now));
    }

    /**
     * 락을 잡기 전에 판정할 수 있는 자격을 검사한다. 물품 상태와 무관해서 락이 필요 없다.
     *
     * <p>여기서 거른 요청은 {@code findByIdForUpdate}에 도달하지 않는다. 물품 ID를 훑으며
     * 던지는 요청이 물품 행 락을 잡으면 같은 물품에 몰린 정상 입찰이 그만큼 뒤로 밀린다.
     *
     * <p>참여 여부는 {@code auction_participants}에 {@code agreed_at}이 채워진 행이 있는지로
     * 본다. 그 행은 공유 코드를 알고 로그인한 사용자가 약관 동의 API를 부를 때만 생긴다.
     *
     * <p><b>판매자 검사를 참여 검사보다 먼저 한다.</b> 판매자는 자기 방에 약관 동의를 하지
     * 않아 참여 행이 없다. 순서가 반대면 판매자가 {@code TERMS_NOT_AGREED}를 받고, 화면은
     * 약관에 동의하라고 안내하는데, 동의해도 그다음엔 {@code SELLER_CANNOT_BID}로 다시
     * 거절된다.
     */
    private void validateEntitled(Long auctionItemId, User bidder, Long sellerUserId) {

        if (Objects.equals(sellerUserId, bidder.getUserId())) {
            throw new ApplicationException(BidErrorType.SELLER_CANNOT_BID);
        }

        if (!auctionItemRepository.existsParticipant(auctionItemId, bidder.getUserId())) {
            throw new ApplicationException(BidErrorType.TERMS_NOT_AGREED);
        }
    }

    /**
     * 락을 잡고 다시 읽은 물품으로 입찰을 받을 수 없는 요청을 거른다.
     *
     * <p>여기 남은 셋은 모두 락 안에서 봐야 하는 값이다. 락 앞에서 읽으면 그 사이에 상태가
     * 바뀔 수 있다. 락이 필요 없는 자격 검사는 {@link #validateEntitled}가 먼저 끝낸다.
     *
     * <p>상태와 마감 시각을 모두 보는 이유는 물품을 진행중으로 바꾸는 코드가 아직 없어
     * {@code endAt}이 지났는데도 진행중으로 남아 있는 물품이 생길 수 있기 때문이다.
     * {@code endAt}이 비어 있으면 마감 판정을 할 수 없으므로 받지 않는다.
     */
    private void validateBiddable(AuctionItem auctionItem, User bidder, LocalDateTime arrivedAt) {

        if (auctionItem.getStatus() != AuctionItemStatus.IN_PROGRESS) {
            throw new ApplicationException(BidErrorType.ITEM_NOT_IN_PROGRESS);
        }

        LocalDateTime endAt = auctionItem.getEndAt();
        if (endAt == null || !arrivedAt.isBefore(endAt)) {
            throw new ApplicationException(BidErrorType.ITEM_CLOSED);
        }

        User leader = auctionItem.getLeaderUser();
        if (leader != null && Objects.equals(leader.getUserId(), bidder.getUserId())) {
            throw new ApplicationException(BidErrorType.ALREADY_TOP_BIDDER);
        }
    }

    /**
     * 금액 규칙을 검증한다.
     *
     * <p>최소 입찰 금액은 입찰이 아직 없으면 시작가, 있으면 현재가 + 단위다. 시작가와 같은
     * 금액으로 첫 입찰을 할 수 있어야 해서 "현재가 초과"가 아니라 이 형태다. 통과한 금액은
     * 항상 {@code 시작가 + 단위 × N} 위에 있으므로 현재가도 그 격자 위에 있다.
     */
    private void validateAmount(AuctionItem auctionItem, Long amount) {

        long minimum = auctionItem.getLeaderUser() == null
                ? auctionItem.getStartingPrice()
                : auctionItem.getCurrentPrice() + auctionItem.getBidIncrement();

        if (amount < minimum) {
            throw new ApplicationException(BidErrorType.BID_AMOUNT_TOO_LOW);
        }

        if ((amount - auctionItem.getStartingPrice()) % auctionItem.getBidIncrement() != 0) {
            throw new ApplicationException(BidErrorType.INVALID_BID_UNIT);
        }
    }

    /**
     * 입찰을 저장한다. {@code (auction_item_id, amount)} unique 위반은 같은 금액이 이미
     * 접수됐다는 뜻이라 거절로 바꾼다. 락이 있어 정상 경로에서는 위 금액 검증이 먼저 걸리므로
     * 여기까지 오는 것은 최후 방어선이 작동한 경우다.
     *
     * <p>{@code saveAndFlush}로 즉시 flush해야 제약 위반을 이 자리에서 잡을 수 있다.
     */
    private Bid saveBid(AuctionItem auctionItem, User bidder, Long amount) {
        try {
            return bidRepository.saveAndFlush(Bid.builder()
                    .auctionItem(auctionItem)
                    .bidder(bidder)
                    .amount(amount)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new ApplicationException(BidErrorType.CONCURRENT_BID_CONFLICT);
        }
    }
}
