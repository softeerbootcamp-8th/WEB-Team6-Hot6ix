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
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
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

    /**
     * 입찰을 접수해 기록을 남기고 물품의 현재가·최고 입찰자를 갱신한다.
     *
     * <p>물품 행에 쓰기 락을 걸고 읽으므로 같은 물품에 대한 입찰이 한 줄로 직렬화된다.
     * 자기 차례에 최신 현재가를 읽기 때문에, 경합에 밀렸어도 금액이 여전히 유효하면 그대로
     * 통과한다. 락을 잡기 전에 끝낼 수 있는 입찰자 조회는 먼저 해서 락 유지 시간을 줄인다.
     *
     * @param auctionItemId 입찰할 물품의 ID
     * @param bidderUserId  입찰자의 회원 ID
     * @param amount        입찰 금액
     * @return 접수된 입찰
     * @throws ApplicationException 입찰자가 없거나(RESOURCE_NOT_FOUND), 물품이 없거나
     *                              (AUCTION_ITEM_NOT_FOUND), 거절 조건에 걸렸을 때(7xxx)
     */
    @Transactional
    public BidCreateResponseDto place(Long auctionItemId, Long bidderUserId, Long amount) {

        User bidder = userRepository.findByUserIdAndDeletedAtIsNull(bidderUserId)
                .orElseThrow(() -> new ApplicationException(CommonErrorType.RESOURCE_NOT_FOUND));

        // 락 시작
        AuctionItem auctionItem = auctionItemRepository.findByIdForUpdate(auctionItemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        validateBiddable(auctionItem, bidder);
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

        return BidCreateResponseDto.from(bid);
    }

    /**
     * 물품 상태로 거절할 것을 거른다.
     *
     * <p>상태와 마감 시각을 모두 본다. 물품을 진행중으로 바꾸는 코드가 아직 없어
     * {@code endAt}이 지났는데도 진행중으로 남아 있는 물품이 생길 수 있기 때문이다.
     * {@code endAt}이 비어 있으면 마감 판정을 할 수 없으므로 받지 않는다.
     */
    private void validateBiddable(AuctionItem auctionItem, User bidder) {

        if (auctionItem.getStatus() != AuctionItemStatus.IN_PROGRESS) {
            throw new ApplicationException(BidErrorType.ITEM_NOT_IN_PROGRESS);
        }

        LocalDateTime endAt = auctionItem.getEndAt();
        if (endAt == null || !LocalDateTime.now().isBefore(endAt)) {
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
