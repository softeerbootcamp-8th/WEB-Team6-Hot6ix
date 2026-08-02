package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemStartRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.exception.ProductErrorType;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionItemService {

    /**
     * 한 경매방에서 동시에 진행할 수 있는 물품 수(기능명세 D002). DB 제약으로는 표현할 수 없어
     * {@link #start}의 경매방 행 락이 이 규칙을 지탱한다 — 물품을 진행중으로 바꾸는 코드가
     * 그 락을 거치지 않으면 제한이 조용히 뚫린다.
     */
    private static final int MAX_IN_PROGRESS_PER_ROOM = 3;

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionRoomRepository auctionRoomRepository;
    private final ProductRepository productRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 경매방의 물품 목록을 상태 우선 순서로 조회한다.
     *
     * @param auctionRoomId 조회할 경매방의 ID
     * @return 물품 요약 목록. 물품이 없으면 빈 목록
     * @throws ApplicationException 경매방이 없거나 soft delete 되었을 때(AUCTION_ROOM_NOT_FOUND)
     */
    public List<AuctionItemSummaryResponseDto> getSummaries(Long auctionRoomId) {
        if (!auctionRoomRepository.existsByAuctionRoomIdAndDeletedAtIsNull(auctionRoomId)) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
        }
        return auctionItemRepository.findSummaries(auctionRoomId);
    }

    /**
     * 물품 상세를 조회한다. 상태로 거르지 않으므로 낙찰·유찰된 물품도 조회된다.
     *
     * @param auctionItemId 조회할 물품의 ID
     * @return 물품 상세
     * @throws ApplicationException 물품이 없을 때(AUCTION_ITEM_NOT_FOUND)
     */
    public AuctionItemDetailResponseDto getDetail(Long auctionItemId) {
        return auctionItemRepository.findDetail(auctionItemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));
    }

    /**
     * 소유자 본인의 경매방에 자기 상품을 READY 물품으로 추가한다. 입찰 단위는 요청으로 받지 않고
     * 경매방 값을 그대로 복사해, 한 방의 모든 물품이 같은 단위를 갖게 한다.
     *
     * <p>한 상품은 한 번에 한 경매방에만 올릴 수 있다. 이미 어딘가에 올라가 있으면 상태와 무관하게
     * 거절하며, 물품을 빼면 행이 사라지므로 그 상품은 다시 올릴 수 있다.
     *
     * @param userId        추가를 요청한 회원의 ID
     * @param auctionRoomId 물품을 추가할 경매방의 ID
     * @param request       추가할 상품과 시작가
     * @return 추가된 물품
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               경매방이 없거나 본인 소유가 아닐 때(AUCTION_ROOM_NOT_FOUND),
     *                               경매방이 종료됐을 때(AUCTION_ROOM_CLOSED),
     *                               상품이 없거나 본인 소유가 아닐 때(PRODUCT_NOT_FOUND),
     *                               이미 경매방에 올라간 상품일 때(PRODUCT_ALREADY_IN_AUCTION)
     */
    @Transactional
    public AuctionItemDetailResponseDto add(Long userId, Long auctionRoomId, AuctionItemAddRequestDto request) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        AuctionRoom auctionRoom = findOwnedRoom(sellerProfile, auctionRoomId);

        if (auctionRoom.getStatus() == AuctionRoomStatus.CLOSED) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_CLOSED);
        }

        Product product = findOwnedProduct(sellerProfile, request.productId());

        if (auctionItemRepository.existsByProduct_ProductId(product.getProductId())) {
            throw new ApplicationException(AuctionErrorType.PRODUCT_ALREADY_IN_AUCTION);
        }

        AuctionItem auctionItem = AuctionItem.from(auctionRoom, product, request);

        return AuctionItemDetailResponseDto.from(save(auctionItem));
    }

    /**
     * 소유자 본인의 경매방에서 아직 시작하지 않은 물품을 뺀다. {@code auction_items}에는
     * {@code deleted_at}이 없어 물리 삭제한다 — 행이 사라지면서 그 상품은 다시 다른 방에
     * 올릴 수 있게 되고, 상품 목록의 파생 상태도 저절로 "미등록"으로 돌아간다.
     *
     * <p>경매방 상태는 보지 않는다. 시작한 적 없는 물품을 빼는 건 방이 어떤 상태든 안전하다.
     *
     * <p>물품 행에 쓰기 락을 걸고 읽는다. 상태를 검사한 뒤 지우는 흐름이라, 락이 없으면 검사와
     * 삭제 사이에 그 물품이 시작돼 <b>진행 중인 경매가 통째로 사라질</b> 수 있다. 다만 이 배제가
     * 성립하려면 물품을 시작하는 쪽도 같은 락을 잡아야 한다.
     *
     * @param userId        제외를 요청한 회원의 ID
     * @param auctionRoomId 물품이 속한 경매방의 ID
     * @param auctionItemId 뺄 물품의 ID
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               경매방이 없거나 본인 소유가 아닐 때(AUCTION_ROOM_NOT_FOUND),
     *                               물품이 없거나 그 방 소속이 아닐 때(AUCTION_ITEM_NOT_FOUND),
     *                               이미 시작된 물품일 때(AUCTION_ITEM_ALREADY_STARTED)
     */
    @Transactional
    public void remove(Long userId, Long auctionRoomId, Long auctionItemId) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        assertRoomOwned(sellerProfile, auctionRoomId);

        AuctionItem auctionItem = auctionItemRepository.findByIdForUpdate(auctionItemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        if (!auctionItem.getAuctionRoom().getAuctionRoomId().equals(auctionRoomId)) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
        }

        if (auctionItem.getStatus() != AuctionItemStatus.READY) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ITEM_ALREADY_STARTED);
        }

        auctionItemRepository.delete(auctionItem);
    }

    /**
     * 소유자 본인의 대기(READY) 물품 경매를 시작한다. 상태를 진행중으로 바꾸고 마감 시각을
     * 확정한 뒤 {@code ItemStarted}를 발행한다. <b>마감 스케줄 등록은 하지 않는다</b> —
     * 스케줄러를 만드는 후속 작업이 등록까지 함께 맡는다.
     *
     * <p>첫 물품이 시작되면 경매방도 {@code BEFORE}에서 {@code OPEN}으로 바뀐다. 판매자가 물품을
     * 시작하는 순간이 곧 방송 시작이라는 뜻이다. 이 값은 <b>입장을 막는 데 쓰이지 않는다</b> —
     * 경매방 조회는 상태와 무관하게 누구에게나 열려 있고, 구매자 화면이 "시작 전"과 "LIVE"
     * 표시를 가르는 데만 쓴다.
     *
     * <p><b>행 두 개에 쓰기 락을 건다. 순서는 항상 물품 → 경매방이다.</b> 물품 락은 같은 물품을
     * 두고 시작·제외·입찰이 엉키는 것을 막고, 경매방 락은 "방당 동시 3개" 검사를 지탱한다 —
     * 개수를 세고 나서 바꾸는 흐름이라 물품 락만으로는 <b>서로 다른</b> 물품에 대한 동시 요청
     * 두 건이 함께 통과할 수 있다. 반대 순서로 잡는 코드를 만들면 데드락이 생긴다.
     *
     * <p>경매방 락이 3개 제한을 지탱한다는 것은, 물품을 진행중으로 바꾸는 경로가 이 메서드
     * 하나뿐이고 그 경로가 모두 이 락을 지난다는 뜻이다. 락을 거치지 않고 진행중으로 바꾸는
     * 코드가 생기면 제한은 조용히 뚫린다. DB 제약으로는 "3개까지"를 표현할 수 없다.
     *
     * @param auctionItemId 시작할 물품의 ID
     * @param userId        시작을 요청한 회원의 ID
     * @param request       경매 시간(분)
     * @return 시작된 물품. 갱신된 상태와 마감 시각이 담긴다
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               물품이 없거나 본인 소유가 아닐 때(AUCTION_ITEM_NOT_FOUND),
     *                               경매방이 종료됐을 때(AUCTION_ROOM_CLOSED),
     *                               대기 중인 물품이 아닐 때(AUCTION_ITEM_NOT_READY),
     *                               이미 3개가 진행 중일 때(AUCTION_ITEM_START_LIMIT_EXCEEDED)
     */
    @Transactional
    public AuctionItemDetailResponseDto start(Long auctionItemId, Long userId,
                                              AuctionItemStartRequestDto request) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);

        AuctionItem auctionItem = auctionItemRepository.findByIdForUpdate(auctionItemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        AuctionRoom auctionRoom = findRoomForUpdate(auctionItem);

        assertRoomOwnedBy(auctionRoom, sellerProfile);
        validateStartable(auctionRoom, auctionItem);

        auctionItem.start(request, LocalDateTime.now());

        if (auctionRoom.getStatus() == AuctionRoomStatus.BEFORE) {
            auctionRoom.open();
        }

        // 리스너가 커밋 후에만 받으므로(DomainEventSseListener) 여기서 발행해도 롤백되면 나가지 않는다.
        domainEventPublisher.publish(ItemStarted.of(
                auctionRoom.getAuctionRoomId(),
                auctionItem.getAuctionItemId(),
                auctionItem.getProduct().getName(),
                auctionItem.getStartedAt()));

        return AuctionItemDetailResponseDto.from(auctionItem);
    }

    /**
     * 물품이 속한 경매방을 쓰기 락을 걸고 읽는다. 시작 API 경로에는 경매방 ID가 없어 물품에서
     * 꺼내 쓴다. 프록시에서 식별자만 꺼내는 것은 초기화를 일으키지 않아 추가 쿼리가 없다.
     *
     * <p>방을 못 찾으면(soft delete된 경우) 4002가 아니라 <b>4001</b>이다. 여기서의 경매방 ID는
     * 요청자가 준 값이 아니라 물품에서 나온 값이라, 방이 없다는 것은 곧 그 물품에 도달할 수
     * 없다는 뜻이기 때문이다.
     */
    private AuctionRoom findRoomForUpdate(AuctionItem auctionItem) {
        return auctionRoomRepository
                .findByIdForUpdate(auctionItem.getAuctionRoom().getAuctionRoomId())
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));
    }

    /**
     * 시작을 받을 수 없는 요청을 거른다. 소유자 확인을 마친 뒤 호출한다.
     *
     * <p>진행 중인 물품 수를 세는 마지막 검사가 셋 중 유일하게 쿼리를 쓰므로 맨 뒤에 둔다.
     * 앞의 두 검사에서 걸리는 요청은 쿼리 없이 끝난다.
     */
    private void validateStartable(AuctionRoom auctionRoom, AuctionItem auctionItem) {

        if (auctionRoom.getStatus() == AuctionRoomStatus.CLOSED) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_CLOSED);
        }

        if (auctionItem.getStatus() != AuctionItemStatus.READY) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_READY);
        }

        if (countInProgress(auctionRoom) >= MAX_IN_PROGRESS_PER_ROOM) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ITEM_START_LIMIT_EXCEEDED);
        }
    }

    /**
     * 경매방이 요청자 소유인지 확인한다. 남의 방이면 물품의 존재 자체를 숨기려고 4001로 응답한다.
     */
    private void assertRoomOwnedBy(AuctionRoom auctionRoom, SellerProfile sellerProfile) {
        if (!auctionRoom.getSellerProfile().getSellerProfileId()
                .equals(sellerProfile.getSellerProfileId())) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
        }
    }

    /**
     * 경매방에서 진행 중인 물품 수를 센다. 이 조회 자체에는 락이 없지만, 호출 시점에 경매방 행을
     * 잡고 있어 같은 방의 다른 시작 요청이 끼어들 수 없다. 물품을 진행중으로 만드는 경로가
     * 그 락을 지나는 시작 메서드뿐이라, 세는 사이 개수가 늘어나지 않는다.
     */
    private long countInProgress(AuctionRoom auctionRoom) {
        return auctionItemRepository.countByAuctionRoom_AuctionRoomIdAndStatus(
                auctionRoom.getAuctionRoomId(), AuctionItemStatus.IN_PROGRESS);
    }

    /**
     * product_id unique 제약 위반을 중복 등록 거절로 바꾼다. 위 존재 검사가 정상 경로를
     * 거르므로, 여기까지 오는 것은 동시 요청 두 건이 함께 통과한 경우다.
     * {@code saveAndFlush}로 즉시 flush해야 제약 위반을 이 자리에서 잡을 수 있다.
     */
    private AuctionItem save(AuctionItem auctionItem) {
        try {
            return auctionItemRepository.saveAndFlush(auctionItem);
        } catch (DataIntegrityViolationException e) {
            throw new ApplicationException(AuctionErrorType.PRODUCT_ALREADY_IN_AUCTION);
        }
    }

    private SellerProfile findActiveSellerProfile(Long userId) {
        return sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));
    }

    private AuctionRoom findOwnedRoom(SellerProfile sellerProfile, Long auctionRoomId) {
        return auctionRoomRepository
                .findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                        auctionRoomId, sellerProfile.getSellerProfileId())
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));
    }

    /**
     * 방이 존재하고 요청자 소유인지만 확인한다. 방 자체가 필요 없는 곳에서 쓴다. 이 검사가 없으면
     * 없는 방 ID로 남의 물품을 지목했을 때 4002(경매방 없음)와 4001(물품 없음)을 구분하지 못한다.
     */
    private void assertRoomOwned(SellerProfile sellerProfile, Long auctionRoomId) {
        if (!auctionRoomRepository.existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                auctionRoomId, sellerProfile.getSellerProfileId())) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
        }
    }

    private Product findOwnedProduct(SellerProfile sellerProfile, Long productId) {
        return productRepository
                .findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                        productId, sellerProfile.getSellerProfileId())
                .orElseThrow(() -> new ApplicationException(ProductErrorType.PRODUCT_NOT_FOUND));
    }
}
