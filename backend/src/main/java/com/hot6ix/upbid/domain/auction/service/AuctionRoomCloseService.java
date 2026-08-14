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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionRoomCloseService {

    /** 아직 결과가 나오지 않은 물품 상태. 하나라도 남아 있으면 자동 종료 대상이 아니다. */
    private static final List<AuctionItemStatus> UNFINISHED_ITEM_STATUSES =
            List.of(AuctionItemStatus.READY, AuctionItemStatus.IN_PROGRESS);

    private final AuctionRoomRepository auctionRoomRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 소유자 본인의 경매방을 종료한다. 방을 {@code CLOSED}로 바꾸고 {@code RoomClosed}를
     * 발행한다. 방송이 끝났다는 것을 서버에 남기는 유일한 수단이며, <b>되돌릴 수 없다</b>
     * (다시 여는 API는 없다).
     *
     * <p><b>진행 중인 물품이 하나라도 있으면 거절한다.</b> 판매자 버튼 하나로 입찰이 붙어 있는
     * 경매가 사라지지 않게 하려는 것이다. 방을 닫으려면 물품을 먼저
     * {@code AuctionItemCloseService.closeEarly}로 앞당겨 마감시켜야 하고, 그러면 마감이
     * 확정되기까지 Soft Close 트리거 초만큼 기다리게 된다. 그 사이 종료가 손이 묶이는 대신,
     * 판매자가 방치한 방은 {@link #closeIfIdle}이 12시간 뒤에 닫는다.
     *
     * <p>아직 시작하지 않은 {@code READY} 물품은 <b>건드리지 않는다.</b> 시작한 적 없는 물품을
     * 유찰로 적으면 "입찰자가 없어 유찰"과 "아예 올리지도 않음"이 결과 집계에서 섞인다.
     * {@code BEFORE} 상태(물품을 하나도 시작하지 않은) 방도 그대로 종료할 수 있다. 쓰다 만 방을
     * 정리하는 수단이기도 하다.
     *
     * <p><b>{@code READ_COMMITTED}가 진행 중 물품 검사의 나머지 절반이다.</b> 기본값인
     * REPEATABLE READ에서는 트랜잭션의 첫 일반 조회(여기서는 판매자 프로필 조회) 시점에 읽기
     * 뷰가 고정되고, 이후 일반 조회는 그 스냅샷만 본다. 그러면 <b>경매방 락을 기다리는 동안
     * 다른 요청이 커밋한 물품 시작을 개수 세기가 보지 못해</b>, 락이 요청을 줄 세워도 낡은 값으로
     * 통과시킨다. 결과는 진행 중인 물품을 남겨 둔 채 방이 닫히는 것이다.
     * {@code AuctionItemService.start}의 "방당 동시 3개" 검사가 같은 이유로 같은 설정을 쓴다.
     *
     * @param userId        종료를 요청한 회원의 ID
     * @param auctionRoomId 종료할 경매방의 ID
     * @return 종료된 경매방
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               경매방이 없거나 본인 소유가 아닐 때(AUCTION_ROOM_NOT_FOUND),
     *                               이미 종료된 경매방일 때(AUCTION_ROOM_CLOSED),
     *                               진행 중인 물품이 남아 있을 때(AUCTION_ROOM_HAS_IN_PROGRESS_ITEM)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuctionRoomPublicResponseDto close(Long userId, Long auctionRoomId) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        assertRoomOwned(sellerProfile, auctionRoomId);

        AuctionRoom auctionRoom = auctionRoomRepository.findByIdForUpdate(auctionRoomId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        if (auctionRoom.getStatus() == AuctionRoomStatus.CLOSED) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_CLOSED);
        }

        if (hasInProgressItem(auctionRoomId)) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_HAS_IN_PROGRESS_ITEM);
        }

        closeLocked(auctionRoom, LocalDateTime.now());

        // 소유 확인을 통과했으므로 요청자가 곧 소유자다.
        return AuctionRoomPublicResponseDto.from(
                auctionRoom,
                auctionItemRepository.countByAuctionRoom_AuctionRoomId(auctionRoomId),
                true, null);
    }

    /**
     * 물품이 전부 마감된 채 방치된 경매방을 <b>소유자 확인 없이</b> 종료한다.
     * {@code AuctionRoomIdleCloseRunner}가 부르는 시스템용 진입점이며, 종료되면 수동 종료와
     * 똑같은 {@code RoomClosed}가 나간다.
     *
     * <p>대상을 고르는 것은 {@code AuctionRoomRepository.findIdleRoomIds}지만, <b>여기서 방 행
     * 락을 잡고 같은 조건을 다시 본다.</b> 목록을 읽고 여기까지 오는 사이에 판매자가 물품을
     * 시작했을 수 있어서다. 그러면 방금 시작한 경매가 열리자마자 닫힌다.
     *
     * <p><b>조건에 맞지 않으면 조용히 {@code false}를 준다.</b> 이미 닫힌 방, 물품이 다시
     * 시작된 방, 다른 서버가 먼저 닫은 방이 여기서 함께 걸러진다. 예외를 던지지 않는 것은
     * 이것이 사용자 요청이 아니라 <b>지나가면서 정리하는 일</b>이라, 대상이 아닌 게 정상
     * 흐름이기 때문이다.
     *
     * <p>{@code READ_COMMITTED}인 이유는 {@link #close}와 같다. 락을 기다리는 동안 커밋된
     * 물품 시작을 못 보면 락을 잡고도 낡은 값으로 방을 닫는다.
     *
     * @param auctionRoomId 종료할 경매방의 ID
     * @param idleBefore    마지막 물품 마감 시각이 이보다 앞서야 종료한다
     * @return 실제로 종료했으면 {@code true}
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean closeIfIdle(Long auctionRoomId, LocalDateTime idleBefore) {

        AuctionRoom auctionRoom = auctionRoomRepository.findByIdForUpdate(auctionRoomId)
                .orElse(null);

        if (auctionRoom == null || auctionRoom.getStatus() != AuctionRoomStatus.OPEN) {
            return false;
        }

        if (auctionItemRepository.existsByAuctionRoom_AuctionRoomIdAndStatusIn(
                auctionRoomId, UNFINISHED_ITEM_STATUSES)) {
            return false;
        }

        LocalDateTime lastEndAt = auctionItemRepository.findMaxEndAt(auctionRoomId);

        if (lastEndAt == null || !lastEndAt.isBefore(idleBefore)) {
            return false;
        }

        closeLocked(auctionRoom, LocalDateTime.now());

        return true;
    }

    /**
     * 경매방에 진행 중인 물품이 있는지 본다. 이 조회 자체에는 락이 없어서, 정확한 값이 나오려면
     * <b>경매방 행 락을 쥔 채로</b> {@code READ_COMMITTED} 트랜잭션에서 불러야 한다.
     * 두 조건이 왜 함께 필요한지는 {@link #close}의 설명에 있다.
     */
    private boolean hasInProgressItem(Long auctionRoomId) {
        return auctionItemRepository.countByAuctionRoom_AuctionRoomIdAndStatus(
                auctionRoomId, AuctionItemStatus.IN_PROGRESS) > 0;
    }

    /** 방 행 락을 잡고 종료 대상임을 확인한 방을 실제로 닫는다. */
    private void closeLocked(AuctionRoom auctionRoom, LocalDateTime closedAt) {

        auctionRoom.close(closedAt);

        domainEventPublisher.publish(RoomClosed.of(
                auctionRoom.getAuctionRoomId(), auctionRoom.getName(), closedAt));
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
