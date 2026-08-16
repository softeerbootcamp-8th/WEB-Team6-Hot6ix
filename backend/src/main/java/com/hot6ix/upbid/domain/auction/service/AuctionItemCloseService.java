package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemCloseEarlyRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemCloseEarlyResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisInitializer;
import com.hot6ix.upbid.domain.auction.store.RedisCloseDecision;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
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
    private final DomainEventPublisher domainEventPublisher;
    private final AuctionRedisStore auctionRedisStore;
    private final AuctionRedisInitializer auctionRedisInitializer;

    /**
     * 마감 시각이 지났으면 물품을 마감하고, <b>아직 안 지났으면 닫지 않고 그 시각을 돌려준다.</b>
     * {@code AuctionCloseScheduler}의 예약이 부른다.
     *
     * <p>마감 판정과 {@code CLOSING} 전환, 스냅샷 Stream 기록은 Redis Lua 한 번으로 수행한다.
     * 입찰 Lua와 같은 물품 Hash를 사용하므로 둘 사이에 다른 요청이 끼어들지 않는다. 입찰이
     * 먼저 Soft Close를 만들면 이 호출은 최신 {@code endAt}과 {@code NOT_DUE}를 돌려준다.
     *
     * <p>돌려준 시각으로 예약을 다시 거는 것은 부르는 쪽 몫이다. 여기서 스케줄러를 직접 부르면
     * 스케줄러와 서로를 의존하게 된다.
     *
     * @param auctionItemId 마감할 물품의 ID
     * @return 마감했거나 마감할 물품이 아니면 빈 값. <b>아직 마감 시각이 아니면 다시 예약할 시각</b>
     */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> closeIfDue(Long auctionItemId) {
        RedisCloseDecision decision = requestNaturalCloseWithSeed(auctionItemId);
        return switch (decision) {
            case RedisCloseDecision.Closing ignored -> Optional.empty();
            case RedisCloseDecision.Rejected rejected
                    when rejected.reason() == RedisCloseDecision.Reason.NOT_DUE ->
                    Optional.of(toLocalDateTime(rejected.endAtMillis()));
            case RedisCloseDecision.Rejected rejected
                    when rejected.reason() == RedisCloseDecision.Reason.ITEM_NOT_IN_PROGRESS ->
                    Optional.empty();
            case RedisCloseDecision.Rejected rejected ->
                    throw new IllegalStateException("자연 마감 Lua가 예상하지 않은 결과를 반환했다: " + rejected);
            case RedisCloseDecision.Advanced advanced ->
                    throw new IllegalStateException("자연 마감에서 앞당김 결과가 반환됐다: " + advanced);
        };
    }

    /**
     * 소유자 본인의 진행 중인 물품 마감을 <b>지금부터 요청한 초만큼 뒤</b>로 앞당긴다. 판매자가
     * 반응이 없는 물품을 빨리 넘기거나 "이만큼만 더 받고 닫겠다"고 할 때 쓴다. <b>여기서 물품이
     * 닫히지는 않는다</b> — 실제 마감은 지금까지처럼 새 시각에 깨어난 {@code AuctionCloseScheduler}의
     * 예약이 한다.
     *
     * <p><b>요청이 없으면 트리거 초만큼 남긴다.</b> 이 API 가 처음부터 하던 동작이라 이미 붙어
     * 있는 화면이 그대로 돌아간다.
     *
     * <p>즉시 닫지 않고 최소 트리거만큼은 남겨두는 것은 구매자에게 얼마나 남았는지 알릴 수 있게
     * 하려는 것이다. 앞당긴 뒤에도 Soft Close는 그대로 살아 있어, 연장 구간에 입찰이 들어오면
     * 마감이 다시 밀린다. 곧 이 API는 마감을 <b>확정</b>하지 않고 앞당기기만 한다.
     *
     * <p>소유권·상태·시각 검증과 Hash 갱신, Stream 기록은 Redis Lua 한 번으로 수행한다.
     * 요청 경로는 MySQL 물품 행을 잠그거나 이벤트를 직접 발행하지 않는다. Consumer가 Stream
     * 순서대로 DB를 반영하고 커밋 후 {@code ItemCloseAdvanced}를 발행한다.
     *
     * <p>거절이 셋이고 화면에서 안내가 다르다. 판정은 {@code close-auction.lua}가 하고 여기서는
     * 에러로만 옮긴다. <b>남길 시간을 Lua 에 넘기는 것이 중요하다</b> — 새 마감 시각을 Redis
     * 시계로 계산해야 서버마다 다를 수 있는 시계가 판정에 섞이지 않는다.
     *
     * @param userId        앞당기기를 요청한 회원의 ID
     * @param auctionItemId 마감을 앞당길 물품의 ID
     * @param request       마감까지 남길 초. <b>{@code null}이면 트리거 초</b>다
     * @return 앞당겨진 마감 시각과 그때까지 남은 초
     * @throws ApplicationException 물품이 없거나 본인 소유가 아닐 때(AUCTION_ITEM_NOT_FOUND),
     *                               진행 중인 물품이 아닐 때(AUCTION_ITEM_NOT_IN_PROGRESS),
     *                               이미 마감이 임박했을 때(AUCTION_ITEM_ALREADY_CLOSING_SOON),
     *                               남길 시간이 트리거보다 짧을 때
     *                               (AUCTION_ITEM_CLOSE_EARLY_REMAINING_TOO_SHORT),
     *                               남길 시간이 지금 마감보다 뒤일 때
     *                               (AUCTION_ITEM_CLOSE_EARLY_REMAINING_TOO_LONG)
     */
    @Transactional(readOnly = true)
    public AuctionItemCloseEarlyResponseDto closeEarly(
            Long userId, Long auctionItemId, AuctionItemCloseEarlyRequestDto request) {

        RedisCloseDecision decision =
                requestSellerAdvanceWithSeed(auctionItemId, userId, remainingSecondsOf(request));

        return switch (decision) {
            case RedisCloseDecision.Advanced advanced -> new AuctionItemCloseEarlyResponseDto(
                    auctionItemId, toLocalDateTime(advanced.endAtMillis()), advanced.remainingSeconds());
            case RedisCloseDecision.Rejected rejected
                    when rejected.reason() == RedisCloseDecision.Reason.NOT_OWNER ->
                    throw new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
            case RedisCloseDecision.Rejected rejected
                    when rejected.reason() == RedisCloseDecision.Reason.ITEM_NOT_IN_PROGRESS ->
                    throw new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_IN_PROGRESS);
            case RedisCloseDecision.Rejected rejected
                    when rejected.reason() == RedisCloseDecision.Reason.ALREADY_CLOSING_SOON ->
                    throw new ApplicationException(AuctionErrorType.AUCTION_ITEM_ALREADY_CLOSING_SOON);
            case RedisCloseDecision.Rejected rejected
                    when rejected.reason() == RedisCloseDecision.Reason.REMAINING_TOO_SHORT ->
                    throw new ApplicationException(
                            AuctionErrorType.AUCTION_ITEM_CLOSE_EARLY_REMAINING_TOO_SHORT);
            case RedisCloseDecision.Rejected rejected
                    when rejected.reason() == RedisCloseDecision.Reason.REMAINING_TOO_LONG ->
                    throw new ApplicationException(
                            AuctionErrorType.AUCTION_ITEM_CLOSE_EARLY_REMAINING_TOO_LONG);
            case RedisCloseDecision.Rejected rejected ->
                    throw new IllegalStateException("마감 앞당김 Lua가 예상하지 않은 결과를 반환했다: " + rejected);
            case RedisCloseDecision.Closing closing ->
                    throw new IllegalStateException("마감 앞당김에서 마감 결과가 반환됐다: " + closing);
        };
    }

    private Integer remainingSecondsOf(AuctionItemCloseEarlyRequestDto request) {
        return request != null ? request.remainingSeconds() : null;
    }

    private RedisCloseDecision requestNaturalCloseWithSeed(Long itemId) {
        RedisCloseDecision decision = auctionRedisStore.requestNaturalClose(itemId, System.currentTimeMillis());
        if (decision instanceof RedisCloseDecision.Rejected rejected
                && rejected.reason() == RedisCloseDecision.Reason.KEY_MISSING) {
            auctionRedisInitializer.initialize(itemId);
            return auctionRedisStore.requestNaturalClose(itemId, System.currentTimeMillis());
        }
        return decision;
    }

    private RedisCloseDecision requestSellerAdvanceWithSeed(
            Long itemId, Long userId, Integer remainingSeconds) {

        RedisCloseDecision decision =
                auctionRedisStore.requestSellerAdvance(itemId, userId, remainingSeconds);
        if (decision instanceof RedisCloseDecision.Rejected rejected
                && rejected.reason() == RedisCloseDecision.Reason.KEY_MISSING) {
            auctionRedisInitializer.initialize(itemId);
            return auctionRedisStore.requestSellerAdvance(itemId, userId, remainingSeconds);
        }
        return decision;
    }

    private static LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
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
