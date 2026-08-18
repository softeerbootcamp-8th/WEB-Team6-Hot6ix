package com.hot6ix.upbid.domain.bid.service;

import com.hot6ix.upbid.domain.auction.realtime.AuctionRealtimeSsePublisher;
import com.hot6ix.upbid.domain.auction.realtime.BidderKeyEncoder;
import com.hot6ix.upbid.domain.auction.service.AuctionParticipantRedisReconciler;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import com.hot6ix.upbid.domain.bid.dto.response.BidCreateResponseDto;
import com.hot6ix.upbid.domain.bid.exception.BidErrorType;
import com.hot6ix.upbid.domain.bid.store.RedisBidDecision;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 진행 중 경매의 입찰 판정을 Redis Lua에 위임한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BidService {

    private final Clock clock;
    private final AuctionRedisStore auctionRedisStore;
    private final AuctionRealtimeSsePublisher auctionRealtimeSsePublisher;
    private final BidderKeyEncoder bidderKeyEncoder;
    private final AuctionParticipantRedisReconciler participantRedisReconciler;

    /**
     * Redis에서 입찰 판정, 상태 갱신, 승인 이벤트 기록을 한 번에 수행한다.
     * 화면 SSE는 Redis 승인 직후 best-effort로 발행하고, MySQL 저장과 내부 DomainEvent는
     * Stream Consumer가 비동기로 처리한다.
     */
    public BidCreateResponseDto place(
            Long auctionItemId,
            Long bidderUserId,
            Long amount,
            String requestId) {

        RedisBidDecision decision = auctionRedisStore.evaluateBid(
                auctionItemId, requestId, bidderUserId, amount);
        if (decision instanceof RedisBidDecision.Rejected rejected
                && rejected.reason() == RedisBidDecision.Reason.TERMS_NOT_AGREED
                && participantRedisReconciler.restoreIfAgreed(auctionItemId, bidderUserId)) {
            decision = auctionRedisStore.evaluateBid(
                    auctionItemId, requestId, bidderUserId, amount);
        }

        return switch (decision) {
            case RedisBidDecision.Accepted accepted -> {
                publishRealtimeBestEffort(auctionItemId, accepted);
                yield BidCreateResponseDto.from(
                        auctionItemId, accepted, clock.getZone(), bidderKeyEncoder);
            }
            case RedisBidDecision.Rejected rejected ->
                    throw new ApplicationException(toErrorType(rejected.reason()));
        };
    }

    private void publishRealtimeBestEffort(
            Long auctionItemId,
            RedisBidDecision.Accepted accepted) {

        if (accepted.duplicate()) {
            return;
        }
        try {
            auctionRealtimeSsePublisher.publishAccepted(auctionItemId, accepted);
        } catch (RuntimeException e) {
            // Redis의 입찰 승인과 MySQL 영속화 Stream 기록은 이미 끝났다. 화면 알림 실패가
            // 승인 자체를 실패로 바꾸면 같은 requestId 재시도에서도 결과 의미가 달라진다.
            log.error("Redis 승인 뒤 실시간 SSE 발행 실패: itemId={}, requestId={}",
                    auctionItemId, accepted.requestId(), e);
        }
    }

    private static BidErrorType toErrorType(RedisBidDecision.Reason reason) {
        return switch (reason) {
            case KEY_MISSING, ITEM_NOT_IN_PROGRESS -> BidErrorType.ITEM_NOT_IN_PROGRESS;
            case ITEM_CLOSED -> BidErrorType.ITEM_CLOSED;
            case ALREADY_TOP_BIDDER -> BidErrorType.ALREADY_TOP_BIDDER;
            case BID_AMOUNT_TOO_LOW -> BidErrorType.BID_AMOUNT_TOO_LOW;
            case INVALID_BID_UNIT -> BidErrorType.INVALID_BID_UNIT;
            case SELLER_CANNOT_BID -> BidErrorType.SELLER_CANNOT_BID;
            case TERMS_NOT_AGREED -> BidErrorType.TERMS_NOT_AGREED;
            case IDEMPOTENCY_KEY_CONFLICT -> BidErrorType.IDEMPOTENCY_KEY_CONFLICT;
        };
    }
}
