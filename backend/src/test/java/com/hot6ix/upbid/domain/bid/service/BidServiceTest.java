package com.hot6ix.upbid.domain.bid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import com.hot6ix.upbid.domain.bid.dto.response.BidCreateResponseDto;
import com.hot6ix.upbid.domain.bid.exception.BidErrorType;
import com.hot6ix.upbid.domain.bid.store.RedisBidDecision;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    private static final Long ITEM_ID = 2L;
    private static final Long BIDDER_ID = 10L;
    private static final Long AMOUNT = 15_000L;
    private static final String REQUEST_ID = "bid-request-1";
    private static final long ARRIVED_AT = 1_786_000_000_000L;
    private static final long ACCEPTED_AT = 1_786_000_000_123L;
    private static final long END_AT = 1_786_000_030_000L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private AuctionRedisStore auctionRedisStore;

    private BidService bidService;

    @BeforeEach
    void setUp() {
        bidService = new BidService(
                Clock.fixed(Instant.ofEpochMilli(ARRIVED_AT), ZONE),
                auctionRedisStore);
    }

    @Test
    @DisplayName("Lua가 승인하면 Redis 판정 결과를 입찰 응답으로 반환한다")
    void returnsAcceptedRedisDecision() {

        when(auctionRedisStore.evaluateBid(ITEM_ID, REQUEST_ID, BIDDER_ID, AMOUNT, ARRIVED_AT))
                .thenReturn(new RedisBidDecision.Accepted(
                        REQUEST_ID, AMOUNT, ACCEPTED_AT, END_AT, 30, false));

        BidCreateResponseDto response = bidService.place(
                ITEM_ID, BIDDER_ID, AMOUNT, REQUEST_ID);

        assertThat(response).isEqualTo(new BidCreateResponseDto(
                REQUEST_ID,
                ITEM_ID,
                AMOUNT,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(ACCEPTED_AT), ZONE),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(END_AT), ZONE),
                30));
        verify(auctionRedisStore)
                .evaluateBid(ITEM_ID, REQUEST_ID, BIDDER_ID, AMOUNT, ARRIVED_AT);
    }

    @Test
    @DisplayName("같은 멱등 키의 재요청도 최초 승인과 동일한 응답을 반환한다")
    void returnsSameResponseForDuplicateDecision() {

        RedisBidDecision.Accepted duplicate = new RedisBidDecision.Accepted(
                REQUEST_ID, AMOUNT, ACCEPTED_AT, END_AT, 30, true);
        when(auctionRedisStore.evaluateBid(ITEM_ID, REQUEST_ID, BIDDER_ID, 20_000L, ARRIVED_AT))
                .thenReturn(duplicate);

        BidCreateResponseDto response = bidService.place(
                ITEM_ID, BIDDER_ID, 20_000L, REQUEST_ID);

        assertThat(response.requestId()).isEqualTo(REQUEST_ID);
        assertThat(response.amount()).isEqualTo(AMOUNT);
        assertThat(response.acceptedAt())
                .isEqualTo(LocalDateTime.ofInstant(Instant.ofEpochMilli(ACCEPTED_AT), ZONE));
        assertThat(response.endAt())
                .isEqualTo(LocalDateTime.ofInstant(Instant.ofEpochMilli(END_AT), ZONE));
        assertThat(response.extendedSeconds()).isEqualTo(30);
    }

    @ParameterizedTest(name = "{0}는 {1}로 응답한다")
    @MethodSource("rejections")
    @DisplayName("Lua 거절 사유를 기존 입찰 에러 계약으로 변환한다")
    void mapsRejectionToBidError(
            RedisBidDecision.Reason reason,
            BidErrorType expectedErrorType) {

        when(auctionRedisStore.evaluateBid(ITEM_ID, REQUEST_ID, BIDDER_ID, AMOUNT, ARRIVED_AT))
                .thenReturn(new RedisBidDecision.Rejected(reason));

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, AMOUNT, REQUEST_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(expectedErrorType);
    }

    private static Stream<Arguments> rejections() {
        return Stream.of(
                Arguments.of(RedisBidDecision.Reason.KEY_MISSING, BidErrorType.ITEM_NOT_IN_PROGRESS),
                Arguments.of(RedisBidDecision.Reason.ITEM_NOT_IN_PROGRESS, BidErrorType.ITEM_NOT_IN_PROGRESS),
                Arguments.of(RedisBidDecision.Reason.ITEM_CLOSED, BidErrorType.ITEM_CLOSED),
                Arguments.of(RedisBidDecision.Reason.ALREADY_TOP_BIDDER, BidErrorType.ALREADY_TOP_BIDDER),
                Arguments.of(RedisBidDecision.Reason.BID_AMOUNT_TOO_LOW, BidErrorType.BID_AMOUNT_TOO_LOW),
                Arguments.of(RedisBidDecision.Reason.INVALID_BID_UNIT, BidErrorType.INVALID_BID_UNIT),
                Arguments.of(RedisBidDecision.Reason.SELLER_CANNOT_BID, BidErrorType.SELLER_CANNOT_BID),
                Arguments.of(RedisBidDecision.Reason.TERMS_NOT_AGREED, BidErrorType.TERMS_NOT_AGREED));
    }
}
