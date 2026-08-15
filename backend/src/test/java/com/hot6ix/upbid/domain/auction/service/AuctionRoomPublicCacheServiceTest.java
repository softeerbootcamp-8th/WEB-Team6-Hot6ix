package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.cache.AuctionRoomPublicSnapshot;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AuctionRoomPublicCacheServiceTest {

    private static final String SHARE_CODE = "aBcD1234aBcD1234";
    private static final Long ROOM_ID = 10L;
    private static final Long SELLER_USER_ID = 3L;

    @Mock
    private AuctionRoomRepository auctionRoomRepository;

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private AuctionRoomPublicCacheService auctionRoomPublicCacheService;

    @Test
    @DisplayName("방과 물품 수를 함께 담고 주인의 회원 ID도 들고 온다")
    void findSnapshot() {

        when(auctionRoomRepository.findByShareCodeAndDeletedAtIsNull(SHARE_CODE))
                .thenReturn(Optional.of(newAuctionRoom()));
        when(auctionItemRepository.countByAuctionRoom_AuctionRoomId(ROOM_ID)).thenReturn(3L);

        AuctionRoomPublicSnapshot snapshot = auctionRoomPublicCacheService.findSnapshot(SHARE_CODE);

        assertThat(snapshot.auctionRoomId()).isEqualTo(ROOM_ID);
        assertThat(snapshot.room().itemCount()).isEqualTo(3L);
        assertThat(snapshot.room().name()).isEqualTo("승민의 경매방");
        // 보는 사람 몫은 담지 않는다. 담으면 남의 것을 보여주게 된다.
        assertThat(snapshot.room().isOwner()).isFalse();
        assertThat(snapshot.room().agreedToTerms()).isNull();
        assertThat(snapshot.isOwnedBy(SELLER_USER_ID)).isTrue();
        assertThat(snapshot.isOwnedBy(SELLER_USER_ID + 1)).isFalse();
        assertThat(snapshot.isOwnedBy(null)).isFalse();
    }

    @Test
    @DisplayName("없는 공유 코드는 예외라 캐시에 담기지 않는다")
    void findSnapshot_notFound() {

        when(auctionRoomRepository.findByShareCodeAndDeletedAtIsNull("unknownShareCode"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionRoomPublicCacheService.findSnapshot("unknownShareCode"))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("트랜잭션 밖에서 지우면 바로 지운다")
    void evict_withoutTransaction() {

        when(cacheManager.getCache(AuctionRoomPublicCacheService.ROOM_PUBLIC_CACHE)).thenReturn(cache);

        auctionRoomPublicCacheService.evict(SHARE_CODE);

        verify(cache).evict(SHARE_CODE);
    }

    /**
     * 커밋 전에 지우면 아직 옛 값이 보이는 동안 다른 요청이 조회를 해서 그 옛 값을 도로 담는다.
     * 그 값은 TTL이 끝날 때까지 남는다.
     */
    @Test
    @DisplayName("트랜잭션 안에서는 커밋된 뒤에야 지운다")
    void evict_afterCommit() {

        TransactionSynchronizationManager.initSynchronization();

        try {
            auctionRoomPublicCacheService.evict(SHARE_CODE);

            verifyNoInteractions(cacheManager);
            verify(cache, never()).evict(SHARE_CODE);

            when(cacheManager.getCache(AuctionRoomPublicCacheService.ROOM_PUBLIC_CACHE)).thenReturn(cache);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(cache).evict(SHARE_CODE);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * 커밋이 끝난 뒤에 도는 코드라, 여기서 예외를 내보내면 DB에는 반영됐는데 응답은 500이 된다.
     */
    @Test
    @DisplayName("Redis가 죽어서 못 지워도 요청은 실패하지 않는다")
    void evict_ignoresRedisFailure() {

        when(cacheManager.getCache(AuctionRoomPublicCacheService.ROOM_PUBLIC_CACHE)).thenReturn(cache);
        doThrow(new RedisConnectionFailureException("연결 없음")).when(cache).evict(SHARE_CODE);

        assertThatCode(() -> auctionRoomPublicCacheService.evict(SHARE_CODE))
                .doesNotThrowAnyException();
    }

    private AuctionRoom newAuctionRoom() {

        User user = User.builder()
                .email("seller@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build();
        ReflectionTestUtils.setField(user, "userId", SELLER_USER_ID);

        SellerProfile sellerProfile = SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .build();

        AuctionRoom auctionRoom = AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name("승민의 경매방")
                .shareCode(SHARE_CODE)
                .status(AuctionRoomStatus.BEFORE)
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();
        ReflectionTestUtils.setField(auctionRoom, "auctionRoomId", ROOM_ID);

        return auctionRoom;
    }
}
