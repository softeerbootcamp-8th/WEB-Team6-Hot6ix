package com.hot6ix.upbid.domain.auction.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.bid.repository.TopBidderProjection;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.hot6ix.upbid.domain.bid.stream.BidStreamMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AuctionRedisInitializerTest extends AbstractRedisContainerTest {

    private static final long ITEM_ID = 101L;
    private static final long ROOM_ID = 202L;
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 8, 13, 21, 0);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private AuctionItemRepository auctionItemRepository;
    private AuctionParticipantRepository auctionParticipantRepository;
    private BidRepository bidRepository;
    private AuctionRedisInitializer initializer;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = redisConnectionFactory();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        redis.delete(List.of(
                AuctionRedisKeys.item(ITEM_ID),
                AuctionRedisKeys.leaderboard(ITEM_ID),
                AuctionRedisKeys.participants(ROOM_ID),
                AuctionRedisKeys.participantNicknames(ROOM_ID)));
        auctionItemRepository = mock(AuctionItemRepository.class);
        auctionParticipantRepository = mock(AuctionParticipantRepository.class);
        bidRepository = mock(BidRepository.class);
        initializer = new AuctionRedisInitializer(
                auctionItemRepository,
                auctionParticipantRepository,
                bidRepository,
                new AuctionRedisStore(redis, new BidStreamMetrics(new SimpleMeterRegistry())));
    }

    @Test
    @DisplayName("시작된 물품과 동의 참여자를 DB에서 읽어 Redis 입찰 상태로 준비한다")
    void initializesStartedItemFromDatabaseSnapshot() {

        AuctionRoom room = mock(AuctionRoom.class);
        when(room.getAuctionRoomId()).thenReturn(ROOM_ID);
        when(room.getSoftCloseTriggerSeconds()).thenReturn(60);
        when(room.getSoftCloseExtendSeconds()).thenReturn(90);

        AuctionItem item = mock(AuctionItem.class);
        Product product = mock(Product.class);
        when(item.getAuctionItemId()).thenReturn(ITEM_ID);
        when(item.getAuctionRoom()).thenReturn(room);
        when(item.getProduct()).thenReturn(product);
        when(product.getName()).thenReturn("한정판 피규어");
        when(item.getStatus()).thenReturn(AuctionItemStatus.IN_PROGRESS);
        when(item.getStartingPrice()).thenReturn(10_000L);
        when(item.getCurrentPrice()).thenReturn(10_000L);
        when(item.getBidIncrement()).thenReturn(1_000L);
        when(item.getEndAt()).thenReturn(END_AT);
        when(item.getTotalExtensionSeconds()).thenReturn(0);

        when(auctionItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(auctionItemRepository.findSellerUserId(ITEM_ID)).thenReturn(Optional.of(303L));
        AuctionParticipantRepository.AgreedParticipant first =
                mock(AuctionParticipantRepository.AgreedParticipant.class);
        when(first.getUserId()).thenReturn(11L);
        when(first.getNickname()).thenReturn("한기");
        AuctionParticipantRepository.AgreedParticipant second =
                mock(AuctionParticipantRepository.AgreedParticipant.class);
        when(second.getUserId()).thenReturn(12L);
        when(second.getNickname()).thenReturn("원기");
        when(auctionParticipantRepository.findAgreedParticipants(ROOM_ID))
                .thenReturn(List.of(first, second));
        TopBidderProjection topBidder = mock(TopBidderProjection.class);
        when(topBidder.getAuctionItemId()).thenReturn(ITEM_ID);
        when(topBidder.getBidderUserId()).thenReturn(12L);
        when(topBidder.getNickname()).thenReturn("원기");
        when(topBidder.getAmount()).thenReturn(12_000L);
        when(bidRepository.findTopBidders(List.of(ITEM_ID), 3)).thenReturn(List.of(topBidder));

        initializer.initialize(ITEM_ID);

        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "status"))
                .isEqualTo("IN_PROGRESS");
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "endAt"))
                .isEqualTo(String.valueOf(END_AT.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "itemName"))
                .isEqualTo("한정판 피규어");
        assertThat(redis.opsForSet().members(AuctionRedisKeys.participants(ROOM_ID)))
                .contains("11", "12");
        assertThat(redis.opsForHash().entries(AuctionRedisKeys.participantNicknames(ROOM_ID)))
                .containsAllEntriesOf(java.util.Map.of("11", "한기", "12", "원기"));
        assertThat(redis.opsForZSet().score(AuctionRedisKeys.leaderboard(ITEM_ID), "12"))
                .isEqualTo(12_000D);
    }

    @Test
    @DisplayName("정상 Redis Seed가 있으면 전체 참여자를 DB에서 다시 읽지 않는다")
    void skipsParticipantSnapshotWhenSeedIsReady() {

        AuctionRoom room = mock(AuctionRoom.class);
        when(room.getAuctionRoomId()).thenReturn(ROOM_ID);
        when(room.getSoftCloseTriggerSeconds()).thenReturn(60);
        when(room.getSoftCloseExtendSeconds()).thenReturn(90);

        AuctionItem item = mock(AuctionItem.class);
        Product product = mock(Product.class);
        when(item.getAuctionItemId()).thenReturn(ITEM_ID);
        when(item.getAuctionRoom()).thenReturn(room);
        when(item.getProduct()).thenReturn(product);
        when(product.getName()).thenReturn("한정판 피규어");
        when(item.getStatus()).thenReturn(AuctionItemStatus.IN_PROGRESS);
        when(item.getStartingPrice()).thenReturn(10_000L);
        when(item.getCurrentPrice()).thenReturn(10_000L);
        when(item.getBidIncrement()).thenReturn(1_000L);
        when(item.getEndAt()).thenReturn(END_AT);
        when(item.getTotalExtensionSeconds()).thenReturn(0);

        when(auctionItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(auctionItemRepository.findSellerUserId(ITEM_ID)).thenReturn(Optional.of(303L));
        AuctionParticipantRepository.AgreedParticipant participant =
                mock(AuctionParticipantRepository.AgreedParticipant.class);
        when(participant.getUserId()).thenReturn(11L);
        when(participant.getNickname()).thenReturn("한기");
        when(auctionParticipantRepository.findAgreedParticipants(ROOM_ID))
                .thenReturn(List.of(participant));

        initializer.initialize(ITEM_ID);
        initializer.initialize(ITEM_ID);

        verify(auctionItemRepository, times(1)).findSellerUserId(ITEM_ID);
        verify(auctionParticipantRepository, times(1)).findAgreedParticipants(ROOM_ID);
    }

    @Test
    @DisplayName("복구 대상 조회 뒤 마감된 물품은 Redis에 새로 준비하지 않는다")
    void skipsItemThatIsNoLongerInProgress() {

        AuctionRoom room = mock(AuctionRoom.class);
        when(room.getAuctionRoomId()).thenReturn(ROOM_ID);
        when(room.getSoftCloseTriggerSeconds()).thenReturn(60);
        when(room.getSoftCloseExtendSeconds()).thenReturn(90);

        AuctionItem item = mock(AuctionItem.class);
        when(item.getAuctionItemId()).thenReturn(ITEM_ID);
        when(item.getAuctionRoom()).thenReturn(room);
        when(item.getStatus()).thenReturn(AuctionItemStatus.SOLD);
        when(item.getStartingPrice()).thenReturn(10_000L);
        when(item.getCurrentPrice()).thenReturn(15_000L);
        when(item.getBidIncrement()).thenReturn(1_000L);
        when(item.getEndAt()).thenReturn(END_AT);
        when(item.getTotalExtensionSeconds()).thenReturn(0);

        when(auctionItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(auctionItemRepository.findSellerUserId(ITEM_ID)).thenReturn(Optional.of(303L));

        initializer.initialize(ITEM_ID);

        assertThat(redis.hasKey(AuctionRedisKeys.item(ITEM_ID))).isFalse();
    }
}
