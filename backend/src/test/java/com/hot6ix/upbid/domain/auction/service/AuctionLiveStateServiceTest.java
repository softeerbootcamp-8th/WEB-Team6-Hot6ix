package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionLiveStateResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionLiveLeaderboardEntryResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionLiveStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.realtime.BidderKeyEncoder;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisInitializer;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisLeaderboardEntry;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisLiveState;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisLiveStatus;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuctionLiveStateServiceTest {

    private static final String SHARE_CODE = "abcdefghij123456";
    private static final long ROOM_ID = 10L;
    private static final long ITEM_ID = 30L;

    @Mock
    private AuctionRoomShareService auctionRoomShareService;
    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private AuctionRedisInitializer auctionRedisInitializer;
    @Mock
    private AuctionRedisStore auctionRedisStore;
    @Mock
    private BidderKeyEncoder bidderKeyEncoder;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-17T12:00:00Z"), ZoneId.of("Asia/Seoul"));

    private AuctionLiveStateService auctionLiveStateService;

    @BeforeEach
    void setUp() {
        auctionLiveStateService = new AuctionLiveStateService(
                auctionRoomShareService,
                auctionItemRepository,
                auctionRedisInitializer,
                auctionRedisStore,
                bidderKeyEncoder,
                clock);
    }

    @Test
    @DisplayName("진행 중 물품은 MySQL이 아니라 Redis 한 시점의 상태와 익명 리더보드를 반환한다")
    void returnsRedisLiveStates() {
        AuctionItem item = mock(AuctionItem.class);
        when(item.getAuctionItemId()).thenReturn(ITEM_ID);
        when(auctionRoomShareService.resolveRoomId(SHARE_CODE)).thenReturn(ROOM_ID);
        when(auctionItemRepository.findByAuctionRoom_AuctionRoomIdAndStatus(
                ROOM_ID, AuctionItemStatus.IN_PROGRESS)).thenReturn(List.of(item));
        when(auctionRedisStore.findLiveState(ITEM_ID)).thenReturn(Optional.of(
                new AuctionRedisLiveState(
                        ITEM_ID,
                        ROOM_ID,
                        AuctionRedisLiveStatus.IN_PROGRESS,
                        70_000L,
                        42L,
                        Instant.parse("2026-08-17T12:05:00Z").toEpochMilli(),
                        30,
                        7L,
                        List.of(
                                new AuctionRedisLeaderboardEntry(42L, "민수", 70_000L),
                                new AuctionRedisLeaderboardEntry(43L, "민수", 60_000L)))));
        when(bidderKeyEncoder.encode(ROOM_ID, 42L)).thenReturn("bidder-a");
        when(bidderKeyEncoder.encode(ROOM_ID, 43L)).thenReturn("bidder-b");

        List<AuctionLiveStateResponseDto> result =
                auctionLiveStateService.getLiveStates(SHARE_CODE, 42L);

        assertThat(result).singleElement().satisfies(state -> {
            assertThat(state.auctionItemId()).isEqualTo(ITEM_ID);
            assertThat(state.status()).isEqualTo(AuctionLiveStatus.IN_PROGRESS);
            assertThat(state.currentPrice()).isEqualTo(70_000L);
            assertThat(state.endAt()).isEqualTo(LocalDateTime.of(2026, 8, 17, 21, 5));
            assertThat(state.revision()).isEqualTo(7L);
            assertThat(state.leaderboard()).containsExactly(
                    new AuctionLiveLeaderboardEntryResponseDto(
                            1, "bidder-a", "민수", 70_000L, true),
                    new AuctionLiveLeaderboardEntryResponseDto(
                            2, "bidder-b", "민수", 60_000L, false));
        });
        verify(auctionRedisInitializer).initialize(ITEM_ID);
    }

    @Test
    @DisplayName("진행 중 물품이 없으면 Redis를 읽지 않고 빈 Snapshot을 반환한다")
    void returnsEmptyWhenNoLiveItems() {
        when(auctionRoomShareService.resolveRoomId(SHARE_CODE)).thenReturn(ROOM_ID);
        when(auctionItemRepository.findByAuctionRoom_AuctionRoomIdAndStatus(
                ROOM_ID, AuctionItemStatus.IN_PROGRESS)).thenReturn(List.of());

        assertThat(auctionLiveStateService.getLiveStates(SHARE_CODE, null)).isEmpty();

        verify(auctionRedisStore, never()).findLiveState(ITEM_ID);
    }

    @Test
    @DisplayName("Seed 뒤에도 Redis 상태가 없으면 낡은 MySQL 값으로 대체하지 않는다")
    void failsWhenRedisStateIsStillMissing() {
        AuctionItem item = mock(AuctionItem.class);
        when(item.getAuctionItemId()).thenReturn(ITEM_ID);
        when(auctionRoomShareService.resolveRoomId(SHARE_CODE)).thenReturn(ROOM_ID);
        when(auctionItemRepository.findByAuctionRoom_AuctionRoomIdAndStatus(
                ROOM_ID, AuctionItemStatus.IN_PROGRESS)).thenReturn(List.of(item));
        when(auctionRedisStore.findLiveState(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionLiveStateService.getLiveStates(SHARE_CODE, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("30");
    }
}
