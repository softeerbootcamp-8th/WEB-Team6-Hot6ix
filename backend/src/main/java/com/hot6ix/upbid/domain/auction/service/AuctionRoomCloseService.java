package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.event.payload.RoomClosed;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionRoomCloseService {

    private final AuctionRoomRepository auctionRoomRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final AuctionItemCloseService auctionItemCloseService;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 소유자 본인의 경매방을 종료한다. 진행 중이던 물품을 모두 마감한 뒤 방을 {@code CLOSED}로
     * 바꾸고 {@code RoomClosed}를 발행한다. 방송이 끝났다는 것을 서버에 남기는 유일한 수단이며,
     * <b>되돌릴 수 없다</b> — 다시 여는 API는 없다.
     *
     * <p>아직 시작하지 않은 {@code READY} 물품은 <b>건드리지 않는다.</b> 시작한 적 없는 물품을
     * 유찰로 적으면 "입찰자가 없어 유찰"과 "아예 올리지도 않음"이 결과 집계에서 섞인다.
     * {@code BEFORE} 상태(물품을 하나도 시작하지 않은) 방도 그대로 종료할 수 있다 — 쓰다 만 방을
     * 정리하는 수단이기도 하다.
     *
     * <p><b>행 락을 물품(ID 오름차순) → 경매방 순으로 잡는다.</b> 이 순서는 지켜야 하는 규약이다.
     * {@code AuctionItemService.start}가 물품 → 경매방 순으로 잡으므로, 방을 먼저 잡고 물품을 닫으면
     * 반대 순서가 되어 동시에 들어온 시작 요청과 데드락이 난다. 그래서 물품을 <b>먼저</b> 전부 닫고
     * 방 락은 맨 마지막에 잡는다. 물품 사이의 순서도 ID 오름차순으로 고정한다
     * ({@code AuctionItemRepository.findIdsByRoomAndStatus}).
     *
     * <p>물품 마감은 같은 트랜잭션에서 돈다({@code AuctionItemCloseService.close}가
     * {@code REQUIRED}). 하나라도 실패하면 방 종료까지 통째로 롤백돼, 방은 종료됐는데 물품
     * 하나만 진행 중으로 남는 상태가 생기지 않는다. 도메인 이벤트는 커밋 후에 나가므로 롤백된
     * 마감은 화면에도 알려지지 않는다.
     *
     * <p>물품을 닫기 <b>전에</b> 방이 이미 종료됐는지 보지 않는 것은, 종료된 방에는 진행 중인
     * 물품이 있을 수 없어 그 반복문이 어차피 아무 일도 하지 않기 때문이다(마감은 진행 중이 아닌
     * 물품을 조용히 건너뛴다). 중복 종료 요청은 방 락을 잡은 뒤 4004로 거절된다.
     *
     * @param userId        종료를 요청한 회원의 ID
     * @param auctionRoomId 종료할 경매방의 ID
     * @return 종료된 경매방
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               경매방이 없거나 본인 소유가 아닐 때(AUCTION_ROOM_NOT_FOUND),
     *                               이미 종료된 경매방일 때(AUCTION_ROOM_CLOSED)
     */
    @Transactional
    public AuctionRoomPublicResponseDto close(Long userId, Long auctionRoomId) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        assertRoomOwned(sellerProfile, auctionRoomId);

        closeInProgressItems(auctionRoomId);

        AuctionRoom auctionRoom = auctionRoomRepository.findByIdForUpdate(auctionRoomId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        if (auctionRoom.getStatus() == AuctionRoomStatus.CLOSED) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_CLOSED);
        }

        LocalDateTime closedAt = LocalDateTime.now();
        auctionRoom.close(closedAt);

        domainEventPublisher.publish(
                RoomClosed.of(auctionRoomId, auctionRoom.getName(), closedAt));

        return AuctionRoomPublicResponseDto.from(
                auctionRoom, auctionItemRepository.countByAuctionRoom_AuctionRoomId(auctionRoomId));
    }

    /**
     * 진행 중인 물품을 ID 오름차순으로 하나씩 마감한다. 물품마다 낙찰이면 {@code ItemEnded},
     * 유찰이면 {@code ItemPassed}가 함께 발행된다.
     *
     * <p>한 방에서 동시에 진행할 수 있는 물품이 3개까지라 반복은 최대 3회다. 여기서 읽은 ID가
     * 실제 마감 시점에는 이미 닫혀 있을 수 있지만, 마감 쪽이 행 락을 걸고 상태를 다시 보고
     * 판단하므로 문제되지 않는다. 판정 기준은 이 목록이 아니라 DB다.
     */
    private void closeInProgressItems(Long auctionRoomId) {

        List<Long> inProgressItemIds = auctionItemRepository.findIdsByRoomAndStatus(
                auctionRoomId, AuctionItemStatus.IN_PROGRESS);

        inProgressItemIds.forEach(auctionItemCloseService::close);
    }

    /**
     * 방이 존재하고 요청자 소유인지만 확인한다. <b>엔티티를 읽지 않는 것이 이 메서드의 요점이다.</b>
     * 같은 트랜잭션에서 뒤에 락을 걸고 방을 다시 읽는데, 여기서 먼저 읽어두면 영속성 컨텍스트에
     * 그 인스턴스가 남아 {@code findByIdForUpdate}가 락은 잡으면서도 <b>그때 읽은 상태를</b>
     * 돌려준다. 그러면 종료 요청 두 건이 겹쳤을 때 뒤늦게 락을 잡은 쪽이 앞선 종료를 보지 못하고
     * 방을 한 번 더 닫아, 종료 시각을 덮어쓰고 {@code RoomClosed}를 두 번 발행한다.
     * {@code AuctionRoomCloseConcurrencyTest}가 그 상황을 재현한다.
     *
     * <p>락을 건 조회는 스냅샷이 아니라 최신 커밋을 읽으므로, 엔티티를 미리 읽지만 않으면
     * 기본 격리 수준에서도 중복 종료가 걸린다.
     */
    private void assertRoomOwned(SellerProfile sellerProfile, Long auctionRoomId) {
        if (!auctionRoomRepository.existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                auctionRoomId, sellerProfile.getSellerProfileId())) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
        }
    }

    private SellerProfile findActiveSellerProfile(Long userId) {
        return sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));
    }
}
