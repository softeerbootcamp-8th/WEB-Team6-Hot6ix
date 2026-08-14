package com.hot6ix.upbid.domain.bid.service;

import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import com.hot6ix.upbid.domain.bid.dto.response.BidCreateResponseDto;
import com.hot6ix.upbid.domain.bid.exception.BidErrorType;
import com.hot6ix.upbid.domain.bid.store.RedisBidDecision;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 진행 중 경매의 입찰 판정을 Redis Lua에 위임한다. */
@Service
@RequiredArgsConstructor
public class BidService {

    private final Clock clock;
    private final AuctionRedisStore auctionRedisStore;

    /**
     * Redis에서 입찰 판정, 상태 갱신, 승인 이벤트 기록을 한 번에 수행한다.
     * MySQL 저장과 성공 이벤트 발행은 Stream Consumer가 비동기로 처리한다.
     */
    public BidCreateResponseDto place(
            Long auctionItemId,
            Long bidderUserId,
            Long amount,
            String requestId) {

        RedisBidDecision decision = auctionRedisStore.evaluateBid(
                auctionItemId, requestId, bidderUserId, amount, clock.millis());

        return switch (decision) {
            case RedisBidDecision.Accepted accepted ->
                    BidCreateResponseDto.from(auctionItemId, accepted, clock.getZone());
            case RedisBidDecision.Rejected rejected ->
                    throw new ApplicationException(toErrorType(rejected.reason()));
        };
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
        };
    }
}
