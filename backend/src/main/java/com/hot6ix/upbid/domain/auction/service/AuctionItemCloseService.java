package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemCloseEarlyResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.ItemCloseAdvanced;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionItemCloseService {

    private final AuctionItemRepository auctionItemRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 진행 중인 물품을 <b>마감 시각과 관계없이 지금</b> 마감한다. 입찰이 있으면 낙찰({@code SOLD}),
     * 없으면 유찰({@code FAILED})이며 낙찰은 {@code ItemEnded}를, 유찰은 {@code ItemPassed}를
     * 발행한다. 유찰에 {@code ItemEnded}를 쓸 수 없는 것은 그 payload가 낙찰가와 낙찰자를 필수로
     * 요구하기 때문이다.
     *
     * <p>판매자가 경매방을 종료할 때 쓴다. 그때는 아직 마감 시각이 남은 물품도 함께 닫아야 하므로
     * 시각을 보지 않는다. 예약이 만료돼서 닫는 경우는 {@link #closeIfDue}다.
     *
     * <p><b>물품이 없거나 진행 중이 아니면 조용히 끝낸다</b> — 제외된 물품, 이미 마감된 물품,
     * 중복 호출이 여기서 함께 걸러진다.
     *
     * <p>물품 행에 쓰기 락을 걸고 읽으므로 마감과 입찰이 한 줄로 직렬화된다. 입찰이 먼저 커밋되면
     * 그 입찰까지 반영해 닫히고, 마감이 먼저 커밋되면 뒤이은 입찰이 진행중이 아닌 물품으로 거절된다.
     * 마감 직전 입찰이 조용히 사라지는 구간은 없다.
     *
     * @param auctionItemId 마감할 물품의 ID
     */
    @Transactional
    public void close(Long auctionItemId) {

        AuctionItem auctionItem = lockClosableItem(auctionItemId);

        if (auctionItem == null) {
            return;
        }

        closeLocked(auctionItem, LocalDateTime.now());
    }

    /**
     * 마감 시각이 지났으면 물품을 마감하고, <b>아직 안 지났으면 닫지 않고 그 시각을 돌려준다.</b>
     * {@code AuctionCloseScheduler}의 예약이 부른다.
     *
     * <p>예약은 제 시각에 깨지만 이 메서드가 물품 행 락을 기다리는 동안 <b>Soft Close 연장이
     * 커밋될 수 있다.</b> 그러면 예약이 알던 마감 시각은 이미 옛것이고, 상태만 보고 닫으면 방금
     * 들어온 연장이 없던 일이 된다. 그래서 락을 잡고 다시 읽은 {@code end_at}으로 판단한다.
     *
     * <p>돌려준 시각으로 예약을 다시 거는 것은 부르는 쪽 몫이다. 여기서 스케줄러를 직접 부르면
     * 스케줄러와 서로를 의존하게 된다.
     *
     * @param auctionItemId 마감할 물품의 ID
     * @return 마감했거나 마감할 물품이 아니면 빈 값. <b>아직 마감 시각이 아니면 다시 예약할 시각</b>
     */
    @Transactional
    public Optional<LocalDateTime> closeIfDue(Long auctionItemId) {

        AuctionItem auctionItem = lockClosableItem(auctionItemId);

        if (auctionItem == null) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endAt = auctionItem.getEndAt();

        if (endAt != null && now.isBefore(endAt)) {
            log.info("마감 시각이 아직 남아 닫지 않는다: itemId={}, endAt={}", auctionItemId, endAt);
            return Optional.of(endAt);
        }

        closeLocked(auctionItem, now);

        return Optional.empty();
    }

    /**
     * 소유자 본인의 진행 중인 물품 마감을 <b>Soft Close 연장 구간이 열리는 순간</b>으로 앞당긴다.
     * 판매자가 반응이 없는 물품을 빨리 넘기고 싶을 때 쓴다. <b>여기서 물품이 닫히지는 않는다</b> —
     * 실제 마감은 지금까지처럼 새 시각에 깨어난 {@code AuctionCloseScheduler}의 예약이 한다.
     *
     * <p>즉시 닫지 않고 트리거만큼 남겨두는 것은 구매자에게 얼마나 남았는지 알릴 수 있게 하려는
     * 것이다. 앞당긴 뒤에도 Soft Close는 그대로 살아 있어, 그 구간에 입찰이 들어오면 마감이 다시
     * 밀린다. 곧 이 API는 마감을 <b>확정</b>하지 않고 앞당기기만 한다.
     *
     * <p>물품 행에 쓰기 락을 걸고 읽으므로 앞당기기가 입찰·마감과 한 줄로 직렬화된다. 락이 없으면
     * 방금 옮긴 {@code end_at}이 같은 순간에 커밋된 연장에 덮이거나 그 반대가 된다.
     *
     * <p>이미 연장 구간 안이면(= 남은 시간이 트리거보다 짧으면) 거절한다. 받아주면 앞당기기가
     * 오히려 마감을 뒤로 미는 경우가 생긴다.
     *
     * <p>커밋되면 {@code ItemCloseAdvanced}를 발행한다. 두 스케줄러가 이 이벤트로 예약을 새 마감
     * 시각에 맞추고, 화면은 카운트다운을 다시 맞춘다.
     *
     * @param userId        앞당기기를 요청한 회원의 ID
     * @param auctionItemId 마감을 앞당길 물품의 ID
     * @return 앞당겨진 마감 시각과 그때까지 남은 초
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               물품이 없거나 본인 소유가 아닐 때(AUCTION_ITEM_NOT_FOUND),
     *                               진행 중인 물품이 아닐 때(AUCTION_ITEM_NOT_IN_PROGRESS),
     *                               이미 마감이 임박했을 때(AUCTION_ITEM_ALREADY_CLOSING_SOON)
     */
    @Transactional
    public AuctionItemCloseEarlyResponseDto closeEarly(Long userId, Long auctionItemId) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);

        AuctionItem auctionItem = auctionItemRepository.findByIdForUpdate(auctionItemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        assertOwnedBy(auctionItem, sellerProfile);

        if (auctionItem.getStatus() != AuctionItemStatus.IN_PROGRESS) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_IN_PROGRESS);
        }

        LocalDateTime now = LocalDateTime.now();

        if (!auctionItem.closeEarly(now)) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ITEM_ALREADY_CLOSING_SOON);
        }

        AuctionItemCloseEarlyResponseDto response =
                AuctionItemCloseEarlyResponseDto.of(auctionItem, now);

        domainEventPublisher.publish(ItemCloseAdvanced.of(
                auctionItem.getAuctionRoom().getAuctionRoomId(),
                auctionItemId,
                auctionItem.getProduct().getName(),
                response.remainingSeconds(),
                response.endAt(),
                now));

        return response;
    }

    /**
     * 물품이 속한 경매방이 요청자 소유인지 확인한다. 남의 방 물품이면 물품의 존재 자체를 숨기려고
     * 4001로 응답한다. {@code AuctionItemService.start}와 같은 규칙이다.
     *
     * <p>탈퇴한 판매자의 프로필로는 여기까지 오지 않는다 — {@link #findActiveSellerProfile}이
     * 먼저 걸러낸다.
     */
    private void assertOwnedBy(AuctionItem auctionItem, SellerProfile sellerProfile) {
        if (!auctionItem.getAuctionRoom().getSellerProfile().getSellerProfileId()
                .equals(sellerProfile.getSellerProfileId())) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
        }
    }

    private SellerProfile findActiveSellerProfile(Long userId) {
        return sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));
    }

    /**
     * 마감 대상인 물품을 행 락을 걸고 읽는다. 없거나 진행 중이 아니면 {@code null}이다.
     * 예약이 아니라 DB가 판단 기준이라, 이미 지난 예약이 중복으로 실행돼도 여기서 걸린다.
     */
    private AuctionItem lockClosableItem(Long auctionItemId) {

        AuctionItem auctionItem = auctionItemRepository.findByIdForUpdate(auctionItemId)
                .orElse(null);

        if (auctionItem == null || auctionItem.getStatus() != AuctionItemStatus.IN_PROGRESS) {
            log.info("마감할 물품이 아니어서 건너뛴다: itemId={}", auctionItemId);
            return null;
        }

        return auctionItem;
    }

    /** 락을 잡고 마감 대상임을 확인한 물품을 실제로 닫는다. */
    private void closeLocked(AuctionItem auctionItem, LocalDateTime occurredAt) {

        auctionItem.close();

        domainEventPublisher.publish(toEvent(auctionItem, occurredAt));
    }

    /**
     * 리스너가 커밋 후에만 받으므로(DomainEventSseListener) 마감이 롤백되면 이벤트도 나가지 않는다.
     * 낙찰 이벤트에는 D(낙찰 후보 생성)가 걸려 있다.
     *
     * <p><b>두 이벤트 모두 아직 화면까지 가지 않는다.</b> {@code DomainEventSseListener}가 SSE로
     * 내보낼 DTO를 만드는 자리에 이 둘의 분기가 없어 로그만 남는다. 실시간 채널은 B·E 담당이라
     * 여기서 함께 고치지 않는다.
     */
    private DomainEvent toEvent(AuctionItem auctionItem, LocalDateTime occurredAt) {

        Long roomId = auctionItem.getAuctionRoom().getAuctionRoomId();
        Long itemId = auctionItem.getAuctionItemId();
        String itemName = auctionItem.getProduct().getName();

        if (auctionItem.getStatus() == AuctionItemStatus.SOLD) {
            return ItemEnded.of(roomId, itemId, itemName, auctionItem.getCurrentPrice(),
                    auctionItem.getLeaderUser().getNickname(), occurredAt);
        }
        return ItemPassed.of(roomId, itemId, itemName, occurredAt);
    }
}
