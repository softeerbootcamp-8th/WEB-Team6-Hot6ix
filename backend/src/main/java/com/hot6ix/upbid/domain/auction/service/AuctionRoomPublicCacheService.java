package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.cache.AuctionRoomPublicSnapshot;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 공유 코드로 들어오는 조회가 방 때문에 DB를 읽지 않게 한다.
 *
 * <p>링크와 QR로 들어오는 서비스라 이 경로는 링크를 뿌린 직후에 몰린다. 그때 커넥션 풀 앞에
 * 줄이 서는 것이 응답 시간의 대부분이었다 (#320).
 *
 * <p><b>담는 것은 거의 안 바뀌는 값뿐이다.</b> 물품의 현재가와 마감 시각, 상태는 담지 않는다 —
 * 입찰을 받을지 판정하는 값이라 낡으면 틀린 입찰을 받거나 멀쩡한 입찰을 거절한다.
 *
 * <p><b>별도 빈인 이유.</b> {@code @Cacheable}은 프록시가 가로채므로 같은 클래스 안에서 부르면
 * 캐시를 안 탄다. 조회하는 쪽({@link AuctionRoomService})과 나눠 둬야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionRoomPublicCacheService {

    /** 방 기본 정보. 키는 {@code auction:room:public::{shareCode}}가 된다. */
    public static final String ROOM_PUBLIC_CACHE = "auction:room:public";

    private final AuctionRoomRepository auctionRoomRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final CacheManager cacheManager;

    /**
     * 방 기본 정보를 읽는다. 캐시가 맞으면 DB를 안 본다.
     *
     * @param shareCode 공개 URL로 들어온 공유 코드
     * @return 보는 사람과 무관한 부분만 담은 값
     * @throws ApplicationException 해당 공유 코드의 경매방이 없거나 삭제되었을 때(AUCTION_ROOM_NOT_FOUND)
     */
    @Cacheable(cacheNames = ROOM_PUBLIC_CACHE, key = "#shareCode")
    @Transactional(readOnly = true)
    public AuctionRoomPublicSnapshot findSnapshot(String shareCode) {

        AuctionRoom auctionRoom = auctionRoomRepository.findByShareCodeAndDeletedAtIsNull(shareCode)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        return AuctionRoomPublicSnapshot.from(
                auctionRoom,
                auctionItemRepository.countByAuctionRoom_AuctionRoomId(auctionRoom.getAuctionRoomId()));
    }

    /**
     * 방이 바뀌었으니 담아 둔 값을 버린다. 수정과 종료, 첫 물품 시작(방이 OPEN이 된다),
     * 물품 추가·제외(물품 수가 바뀐다)에서 부른다.
     *
     * <p><b>커밋된 뒤에 지운다.</b> 커밋 전에 지우면 아직 옛 값이 보이는 동안 다른 요청이
     * 조회를 해서 그 옛 값을 도로 담아 버린다. 그 값은 TTL이 끝날 때까지 남는다.
     *
     * <p>커밋 뒤로 미뤄도 창이 완전히 닫히지는 않는다. 커밋 직전에 시작한 조회가 지우기보다
     * 늦게 캐시에 쓰면 옛 값이 남는다. 그건 TTL이 걷어간다.
     */
    public void evict(String shareCode) {

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictNow(shareCode);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictNow(shareCode);
            }
        });
    }

    /**
     * 지우다 실패해도 요청을 실패시키지 않는다. 커밋이 끝난 뒤에 도는 코드라 여기서 예외를
     * 내보내면 <b>DB에는 반영됐는데 응답은 500</b>이 된다.
     *
     * <p>대신 그 방은 TTL이 끝날 때까지 옛 값을 보여준다.
     */
    private void evictNow(String shareCode) {
        try {
            Cache cache = cacheManager.getCache(ROOM_PUBLIC_CACHE);

            if (cache != null) {
                cache.evict(shareCode);
            }
        } catch (RuntimeException e) {
            log.warn("경매방 캐시를 못 지웠다. TTL 만료까지 옛 값이 보인다: shareCode={}", shareCode, e);
        }
    }
}
