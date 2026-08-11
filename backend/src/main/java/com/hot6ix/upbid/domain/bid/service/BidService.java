package com.hot6ix.upbid.domain.bid.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

    private final BidRepository bidRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final AuctionParticipantRepository auctionParticipantRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;
    private final AuctionRedisStore auctionRedisStore;
    /**
     * 접수된 입찰을 저장하는 구간만 트랜잭션으로 묶는다. {@code place()}가 트랜잭션 밖에서
     * 돌아야 거절된 요청이 커넥션을 잡지 않으므로, 프록시의 {@code @Transactional}로는
     * 이 범위를 만들 수 없다.
     */
    private final TransactionTemplate transactionTemplate;

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BidCreateResponseDto place(Long auctionItemId, Long bidderUserId, Long amount) {

        long nowMillis = clock.millis();

        long result = auctionRedisStore.evaluateBid(auctionItemId, bidderUserId, amount, nowMillis);

        if (result == AuctionRedisStore.KEY_MISSING) {
            seedFromDatabase(auctionItemId);
            result = auctionRedisStore.evaluateBid(auctionItemId, bidderUserId, amount, nowMillis);
        }

        if (result != AuctionRedisStore.ACCEPTED) {
            throw new ApplicationException(toErrorType(result));
        }

        return persistAccepted(auctionItemId, bidderUserId, amount);
    }

    /**
     * Redis에 물품 HASH가 없을 때 DB에서 읽어 채운다. 물품이 시작될 때가 아니라 첫 입찰이
     * 왔을 때 돈다. 물품 하나에 한 번이고, Redis를 재시작한 뒤에도 같은 경로로 복구된다.
     *
     * <p>물품이 DB에도 없으면 {@code AUCTION_ITEM_NOT_FOUND}다. 이 경로가 물품 존재를
     * 확인하는 유일한 자리다 — Redis에 키가 없다는 것만으로는 "아직 안 채워졌다"와 "그런
     * 물품이 없다"를 가를 수 없다.
     */
    private void seedFromDatabase(Long auctionItemId) {

        transactionTemplate.executeWithoutResult(status -> {
            AuctionItem item = auctionItemRepository.findById(auctionItemId)
                    .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

            // 아직 시작하지 않은 물품은 endAt이 null이라 채울 수가 없다. 스크립트는 endAt만
            // 보므로 status를 보는 자리가 여기 하나 남는다. 물품 하나에 한 번만 도는 경로라
            // 거절 요청이 DB에 닿지 않는다는 성질은 그대로다.
            if (item.getEndAt() == null) {
                throw new ApplicationException(BidErrorType.ITEM_NOT_IN_PROGRESS);
            }

            Long sellerUserId = auctionItemRepository.findSellerUserId(auctionItemId)
                    .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

            Long roomId = item.getAuctionRoom().getAuctionRoomId();

            auctionRedisStore.seed(item, roomId, sellerUserId,
                    auctionParticipantRepository.findAgreedUserIds(roomId));
        });
    }

    /**
     * 접수가 확정된 입찰을 DB에 남긴다. <b>판정은 하지 않는다.</b>
     *
     * <p>판정을 다시 하면 Redis와 DB 값이 다를 때 Lua가 접수한 입찰이 여기서 거절되는데,
     * Lua는 이미 {@code currentPrice}를 올려놨고 그걸 되돌릴 방법이 없다. 그래서
     * {@code findByIdForUpdate}도 안 쓴다 — 판정을 안 하니 물품 행 락을 잡을 이유가 없다.
     *
     * <p>여기서 예외가 나면 사용자는 5xx를 받는다. Redis에서는 접수됐는데 DB에 기록이 없는
     * 상태라, 낙찰 판정이 DB 기준이므로 그 입찰은 후보에서 빠진다. 재시도는 이 브랜치에서
     * 안 짠다 — 지금까지 측정에서 {@code failed_5xx}가 전부 0이라 이 부하에서 안 일어나고,
     * 붙이면 그게 비교군 D의 절반이라 C가 왜 빨라졌는지 못 가린다.
     */
    private BidCreateResponseDto persistAccepted(Long auctionItemId, Long bidderUserId, Long amount) {

        return transactionTemplate.execute(status -> {

            User bidder = userRepository.findByUserIdAndDeletedAtIsNull(bidderUserId)
                    .orElseThrow(() -> new ApplicationException(CommonErrorType.RESOURCE_NOT_FOUND));

            AuctionItem auctionItem = auctionItemRepository.findById(auctionItemId)
                    .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

            Bid bid = saveBid(auctionItem, bidder, amount);

            applyIfNewer(auctionItem, bidder, amount);

            // 리스너가 커밋 후에만 받으므로(DomainEventSseListener) 여기서 발행해도 롤백되면 나가지 않는다.
            domainEventPublisher.publish(BidPlaced.of(
                    auctionItem.getAuctionRoom().getAuctionRoomId(),
                    auctionItem.getAuctionItemId(),
                    auctionItem.getProduct().getName(),
                    bidder.getNickname(),
                    amount,
                    bid.getAcceptedAt()));

            publishIfExtended(auctionItem, LocalDateTime.now(clock));

            return BidCreateResponseDto.from(bid);
        });
    }

    /**
     * 물품의 현재가와 최고 입찰자를 갱신한다. <b>금액이 올라갈 때만</b> 바꾼다.
     *
     * <p>Lua는 같은 물품 입찰을 한 줄로 세우지만, 그 뒤 DB 트랜잭션은 각자라서 커밋 순서가
     * 뒤집힐 수 있다. 앱이 2대면 더 그렇다. 그대로 덮어쓰면 10,000이 11,000 뒤에 도착해
     * {@code current_price}가 내려간다. 입찰 판정은 Redis가 하므로 이것 때문에 입찰이
     * 틀리지는 않고, 키가 날아갔을 때 {@link #seedFromDatabase}가 낮은 값을 심는 것이 문제다.
     *
     * <p>첫 입찰은 {@code currentPrice}가 시작가와 같은 값이라 금액이 같을 수 있어서
     * {@code leaderUser}가 비어 있는지로 가른다.
     */
    private void applyIfNewer(AuctionItem auctionItem, User bidder, Long amount) {

        if (auctionItem.getLeaderUser() == null || amount > auctionItem.getCurrentPrice()) {
            auctionItem.applyBid(bidder, amount);
        }
    }

    /** {@code lua/bid.lua}가 돌려준 거절 코드를 에러로 옮긴다. 코드와 뜻은 그 파일에 있다. */
    private static BidErrorType toErrorType(long result) {

        return switch ((int) result) {
            case 1 -> BidErrorType.ITEM_CLOSED;
            case 2 -> BidErrorType.ALREADY_TOP_BIDDER;
            case 3 -> BidErrorType.BID_AMOUNT_TOO_LOW;
            case 4 -> BidErrorType.INVALID_BID_UNIT;
            case 5 -> BidErrorType.SELLER_CANNOT_BID;
            case 6 -> BidErrorType.TERMS_NOT_AGREED;
            // 스크립트가 우리가 모르는 값을 돌려줬다는 뜻이다. 접수로 넘기면 안 되고,
            // 아무 거절 사유나 붙이면 index.csv의 거절 분포가 조용히 틀어진다.
            default -> throw new IllegalStateException("bid.lua가 모르는 값을 돌려줬다: " + result);
        };
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

        // 판정은 Redis의 endAt으로 하므로 바뀐 값을 옮겨야 한다. 안 옮기면 연장은 DB에만
        // 남고 입찰은 옛 마감 시각에 막혀서, 연장이 있으나 마나가 된다.
        auctionRedisStore.updateEndAt(auctionItem.getAuctionItemId(), auctionItem.getEndAt());

        domainEventPublisher.publish(SoftCloseExtended.of(
                auctionItem.getAuctionRoom().getAuctionRoomId(),
                auctionItem.getAuctionItemId(),
                auctionItem.getProduct().getName(),
                auctionItem.getAuctionRoom().getSoftCloseExtendSeconds(),
                auctionItem.getEndAt(),
                now));
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
