package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemBulkAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemStartRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemBulkAddResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.LeaderboardEntryResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.bid.repository.TopBidderProjection;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.exception.ProductErrorType;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.event.payload.ItemAdded;
import com.hot6ix.upbid.global.event.payload.ItemRemoved;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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

    /** 화면이 상위 3명만 그린다({@code leaderboard-rows.tsx}). 클라이언트가 정하게 두지 않는다. */
    private static final int LEADERBOARD_SIZE = 3;

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionRoomRepository auctionRoomRepository;
    private final ProductRepository productRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final BidRepository bidRepository;

    /**
     * 경매방의 물품 목록을 상태 우선 순서로 조회한다. 물품마다 상위
     * {@link #LEADERBOARD_SIZE}명을 함께 담는다.
     *
     * <p>리더보드는 쿼리 한 번으로 물품 전체를 가져와 ID로 묶는다. 물품마다 따로 조회하면
     * 물품 수만큼 쿼리가 늘어난다. 물품이 없으면 조회 자체를 건너뛴다.
     *
     * @param auctionRoomId 조회할 경매방의 ID
     * @return 물품 요약 목록. 물품이 없으면 빈 목록
     * @throws ApplicationException 경매방이 없거나 soft delete 되었을 때(AUCTION_ROOM_NOT_FOUND)
     */
    public List<AuctionItemSummaryResponseDto> getSummaries(Long auctionRoomId) {
        if (!auctionRoomRepository.existsByAuctionRoomIdAndDeletedAtIsNull(auctionRoomId)) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
        }

        List<AuctionItemSummaryResponseDto> summaries =
                auctionItemRepository.findSummaries(auctionRoomId);

        Map<Long, List<LeaderboardEntryResponseDto>> leaderboards = findLeaderboards(
                summaries.stream().map(AuctionItemSummaryResponseDto::auctionItemId).toList());

        return summaries.stream()
                .map(summary -> summary.withLeaderboard(
                        leaderboards.getOrDefault(summary.auctionItemId(), List.of())))
                .toList();
    }

    /**
     * 물품 상세를 조회한다. 상태로 거르지 않으므로 낙찰·유찰된 물품도 조회된다.
     *
     * <p>리더보드는 쿼리를 따로 돌려 붙인다. JPQL 생성자 표현식으로는 List 필드를 채울 수 없다.
     *
     * @param auctionItemId 조회할 물품의 ID
     * @return 물품 상세. 입찰이 없으면 리더보드는 빈 목록
     * @throws ApplicationException 물품이 없을 때(AUCTION_ITEM_NOT_FOUND)
     */
    public AuctionItemDetailResponseDto getDetail(Long auctionItemId) {
        AuctionItemDetailResponseDto detail = auctionItemRepository.findDetail(auctionItemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        return detail.withLeaderboard(
                findLeaderboards(List.of(auctionItemId))
                        .getOrDefault(auctionItemId, List.of()));
    }

    /**
     * 물품들의 리더보드를 쿼리 한 번으로 가져와 물품 ID로 묶는다. 물품마다 따로 조회하면
     * 물품 수만큼 쿼리가 늘어난다.
     *
     * @return 물품 ID → 상위 {@link #LEADERBOARD_SIZE}명. 입찰이 없는 물품은 키가 없다
     */
    private Map<Long, List<LeaderboardEntryResponseDto>> findLeaderboards(List<Long> auctionItemIds) {
        return bidRepository.findTopBidders(auctionItemIds, LEADERBOARD_SIZE).stream()
                .collect(Collectors.groupingBy(
                        TopBidderProjection::getAuctionItemId,
                        Collectors.mapping(LeaderboardEntryResponseDto::from, Collectors.toList())));
    }

    /**
     * 소유자 본인의 경매방에 자기 상품을 READY 물품으로 추가한다. 입찰 단위는 요청으로 받지 않고
     * 경매방 값을 그대로 복사해, 한 방의 모든 물품이 같은 단위를 갖게 한다.
     *
     * <p>재등록을 지원하므로 한 상품이 여러 {@code AuctionItem}을 가질 수 있다. 이미 올라간
     * 물품이 있어도 그게 유찰(FAILED)이거나 낙찰 후 거래가 전원 실패했으면 다시 올릴 수 있다
     * ({@link AuctionItemRepository#findBlockedProductIdsIn} 참고). 진행 중이거나 낙찰돼
     * 거래가 살아 있는 상품만 거절한다.
     *
     * <p>상품 행에 쓰기 락을 걸고 읽는다({@link ProductRepository#findOwnedForUpdate}).
     * 차단 검사와 INSERT 사이에 다른 요청이 끼어들면 같은 상품이 물품 두 개로 동시에 올라갈
     * 수 있다 — {@code auction_items.product_id}의 unique 제약이 없어진 뒤로는 이 락이 유일한
     * 방어선이다. {@code READ_COMMITTED}가 필요한 이유는 {@link #start}의 같은 문단을 본다.
     *
     * <p>저장이 커밋되면 {@code ItemAdded}를 발행해 구독자가 목록을 다시 읽게 한다. 이게 없으면
     * 라이브 중에 추가한 물품이 구매자 화면에 새로고침 전까지 나타나지 않는다.
     *
     * @param userId        추가를 요청한 회원의 ID
     * @param auctionRoomId 물품을 추가할 경매방의 ID
     * @param request       추가할 상품과 시작가
     * @return 추가된 물품
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               경매방이 없거나 본인 소유가 아닐 때(AUCTION_ROOM_NOT_FOUND),
     *                               경매방이 종료됐을 때(AUCTION_ROOM_CLOSED),
     *                               상품이 없거나 본인 소유가 아닐 때(PRODUCT_NOT_FOUND),
     *                               아직 경매·거래가 진행 중인 상품일 때(PRODUCT_ALREADY_IN_AUCTION)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuctionItemDetailResponseDto add(Long userId, Long auctionRoomId, AuctionItemAddRequestDto request) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        AuctionRoom auctionRoom = findOwnedRoom(sellerProfile, auctionRoomId);

        assertRoomNotClosed(auctionRoom);

        Product product = findOwnedProductForUpdate(sellerProfile, request.productId());

        assertNotBlocked(product.getProductId());

        assertWithinLimit(auctionRoomId, 1);

        AuctionItem auctionItem = AuctionItem.from(auctionRoom, product, request);
        AuctionItem saved = auctionItemRepository.save(auctionItem);

        domainEventPublisher.publish(ItemAdded.of(auctionRoomId, LocalDateTime.now(), 1));

        return AuctionItemDetailResponseDto.from(saved);
    }

    /**
     * 소유자 본인의 경매방에 여러 상품을 한 번에 READY 물품으로 추가한다. 판정 규칙은 단건
     * {@link #add}와 같고, <b>다른 점은 거절된 상품이 있어도 나머지는 추가한다는 것</b>이다.
     * 거절된 상품은 사유와 함께 응답의 {@code failed}로 돌려주므로 판매자가 상품을 처음부터
     * 다시 고를 필요가 없다.
     *
     * <p>같은 판매자 프로필·경매방을 상품 수만큼 다시 조회하지 않는 것이 이 메서드의 이유다.
     * 상품 조회·락과 차단 판정도 {@code IN} 절로 한 번에 끝내, 물품이 몇 개든 쿼리 수가 늘지 않는다.
     *
     * <p>저장이 커밋되면 {@code ItemAdded}를 <b>한 번만</b> 발행한다. 물품마다 보내면 구독자가
     * 추가한 수만큼 목록을 다시 읽는다.
     *
     * <p><b>부분 성공이 한 트랜잭션 안에서 성립하는 이유</b>는 거절 판정이 전부 저장 <i>전에</i>
     * 끝나기 때문이다. 거절된 상품은 애초에 INSERT를 시도하지 않으므로 롤백할 것이 없다.
     * {@link ProductRepository#findOwnedForUpdate(java.util.Collection, Long) 상품 행 락}이
     * 상품 집합이 겹치는 동시 요청을 줄 세우므로, {@link #add}와 달리 여기서는 저장 시점에
     * 걸릴 경합이 남지 않는다.
     *
     * @param userId        추가를 요청한 회원의 ID
     * @param auctionRoomId 물품을 추가할 경매방의 ID
     * @param request       추가할 상품과 시작가 목록
     * @return 추가된 물품과 거절된 상품
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               경매방이 없거나 본인 소유가 아닐 때(AUCTION_ROOM_NOT_FOUND),
     *                               경매방이 종료됐을 때(AUCTION_ROOM_CLOSED),
     *                               요청에 같은 상품이 두 번 들어왔을 때(INVALID_REQUEST),
     *                               추가하면 경매방 물품 상한을 넘을 때(AUCTION_ITEM_LIMIT_EXCEEDED)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuctionItemBulkAddResponseDto addAll(Long userId, Long auctionRoomId,
                                                AuctionItemBulkAddRequestDto request) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        AuctionRoom auctionRoom = findOwnedRoom(sellerProfile, auctionRoomId);

        assertRoomNotClosed(auctionRoom);

        List<Long> productIds = request.items().stream()
                .map(AuctionItemAddRequestDto::productId)
                .toList();

        assertNoDuplicate(productIds);

        Map<Long, Product> ownedProducts = productRepository
                .findOwnedForUpdate(productIds, sellerProfile.getSellerProfileId())
                .stream()
                .collect(Collectors.toMap(Product::getProductId, Function.identity()));

        Set<Long> blocked = Set.copyOf(auctionItemRepository.findBlockedProductIdsIn(productIds));

        List<AuctionItemBulkAddResponseDto.Failure> failed = new ArrayList<>();
        List<AuctionItem> candidates = new ArrayList<>();

        for (AuctionItemAddRequestDto item : request.items()) {
            Product product = ownedProducts.get(item.productId());

            if (product == null) {
                failed.add(AuctionItemBulkAddResponseDto.Failure.of(
                        item.productId(), ProductErrorType.PRODUCT_NOT_FOUND));
                continue;
            }
            if (blocked.contains(item.productId())) {
                failed.add(AuctionItemBulkAddResponseDto.Failure.of(
                        item.productId(), AuctionErrorType.PRODUCT_ALREADY_IN_AUCTION));
                continue;
            }
            candidates.add(AuctionItem.from(auctionRoom, product, item));
        }

        // 넣을 게 하나도 없으면 상한을 볼 이유도, 빈 배치를 저장할 이유도 없다.
        if (candidates.isEmpty()) {
            return new AuctionItemBulkAddResponseDto(List.of(), failed);
        }

        // 실제로 들어갈 물품만 센다. 거절된 상품까지 세면 넣지도 않을 것 때문에 상한에 걸린다.
        assertWithinLimit(auctionRoomId, candidates.size());

        List<AuctionItemDetailResponseDto> added = auctionItemRepository.saveAll(candidates).stream()
                .map(AuctionItemDetailResponseDto::from)
                .toList();

        // 물품마다 보내지 않는다. 구독자가 추가한 수만큼 목록을 다시 읽게 된다.
        domainEventPublisher.publish(ItemAdded.of(auctionRoomId, LocalDateTime.now(), added.size()));

        return new AuctionItemBulkAddResponseDto(added, failed);
    }

    /**
     * 소유자 본인의 경매방에서 아직 시작하지 않은 물품을 뺀다. {@code auction_items}에는
     * {@code deleted_at}이 없어 물리 삭제한다 — 행이 사라지면서 그 상품은 다시 다른 방에
     * 올릴 수 있게 되고, 상품 목록의 파생 상태도 저절로 "미등록"으로 돌아간다.
     *
     * <p>경매방 상태는 보지 않는다. 시작한 적 없는 물품을 빼는 건 방이 어떤 상태든 안전하다.
     *
     * <p>삭제가 커밋되면 {@code ItemRemoved}를 발행해 구독자가 목록을 다시 읽게 한다. 이게
     * 없으면 구매자 화면에 빠진 물품이 새로고침 전까지 계속 남는다.
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

        domainEventPublisher.publish(ItemRemoved.of(auctionRoomId, auctionItemId, LocalDateTime.now()));
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
     * <p><b>{@code READ_COMMITTED}가 3개 제한의 나머지 절반이다.</b> 기본값인 REPEATABLE READ에서는
     * 트랜잭션의 첫 일반 조회(여기서는 판매자 프로필 조회) 시점에 읽기 뷰가 고정되고, 이후 일반
     * 조회는 그 시점의 스냅샷만 본다. 그러면 <b>경매방 락을 기다리는 동안 다른 요청이 커밋한
     * 진행중 물품을 개수 세기가 보지 못해</b>, 락이 요청을 줄 세워도 낡은 값으로 통과시킨다.
     * Testcontainers MySQL에서 실측한 결과가
     * {@code AuctionItemStartIsolationTest}에 회귀 테스트로 남아 있다.
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
                auctionItem.getStartedAt(),
                auctionItem.getEndAt()));

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
     * 경매방에서 진행 중인 물품 수를 센다. 이 조회 자체에는 락이 없다. 정확한 값이 나오려면
     * <b>두 가지가 모두</b> 필요하다.
     *
     * <ol>
     *   <li>호출 시점에 경매방 행 락을 쥐고 있을 것 — 세는 동안 다른 시작 요청이 끼어들지 못한다.
     *   <li>트랜잭션이 {@code READ_COMMITTED}일 것 — 기본값인 REPEATABLE READ에서는 락을
     *       <b>기다리는 동안</b> 다른 요청이 커밋한 물품이 스냅샷에 안 잡혀, 락을 잡고도 낡은
     *       값을 센다.
     * </ol>
     *
     * 락만으로 충분하다고 보면 안 된다. 락은 "잡은 뒤"만 막고, 위험 구간은 읽기 뷰가 만들어진
     * 뒤부터 락을 잡기 전까지다.
     */
    private long countInProgress(AuctionRoom auctionRoom) {
        return auctionItemRepository.countByAuctionRoom_AuctionRoomIdAndStatus(
                auctionRoom.getAuctionRoomId(), AuctionItemStatus.IN_PROGRESS);
    }

    private void assertRoomNotClosed(AuctionRoom auctionRoom) {
        if (auctionRoom.getStatus() == AuctionRoomStatus.CLOSED) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_CLOSED);
        }
    }

    /**
     * 요청 안에 같은 상품이 두 번 들어오면 거절한다. 거절 목록에 담지 않고 요청 전체를 무르는
     * 이유는, 담으면 "중복 중 하나는 성공하고 하나는 실패"라는 뜻이 되어 어느 쪽이 들어갔는지
     * 응답만 봐서는 알 수 없기 때문이다. 화면에서는 만들 수 없는 요청이라 방어 목적이다.
     */
    private void assertNoDuplicate(List<Long> productIds) {
        if (productIds.size() != Set.copyOf(productIds).size()) {
            throw new ApplicationException(CommonErrorType.INVALID_REQUEST);
        }
    }

    /**
     * 추가 후 물품 수가 경매방 상한을 넘지 않는지 확인한다. 상한을 <b>등록에서</b> 막는 이유는
     * 목록 조회가 {@link AuctionItemRepository#MAX_SUMMARY_SIZE}건에서 끊기기 때문이다 —
     * 그보다 많이 등록되면 뒤쪽 물품이 에러 없이 목록에서 사라진다. 그래서 두 값은 같아야 하고,
     * 상수를 따로 두지 않고 조회 상한을 그대로 참조한다.
     *
     * <p>상한을 넘기면 앞에서부터 잘라 넣지 않고 요청 전체를 거절한다. 잘라 넣으면 판매자가
     * 무엇이 빠졌는지 알 수 없다.
     */
    private void assertWithinLimit(Long auctionRoomId, int addCount) {
        long current = auctionItemRepository.countByAuctionRoom_AuctionRoomId(auctionRoomId);
        if (current + addCount > AuctionItemRepository.MAX_SUMMARY_SIZE) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ITEM_LIMIT_EXCEEDED);
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

    /**
     * 소유자 확인과 상품 행 쓰기 락을 한 쿼리로 끝낸다({@link ProductRepository#findOwnedForUpdate}).
     * 물품 추가는 "차단 상태인지 보고 → INSERT" 흐름이라 락 없이는 동시 요청 두 건이 함께
     * 통과할 수 있다.
     */
    private Product findOwnedProductForUpdate(SellerProfile sellerProfile, Long productId) {
        return productRepository
                .findOwnedForUpdate(productId, sellerProfile.getSellerProfileId())
                .orElseThrow(() -> new ApplicationException(ProductErrorType.PRODUCT_NOT_FOUND));
    }

    /**
     * 상품에 재등록을 막는 물품이 있으면 거절한다. 상품 행 락을 먼저 잡은 뒤에 호출해야
     * 검사와 INSERT 사이에 다른 요청이 끼어들지 못한다.
     */
    private void assertNotBlocked(Long productId) {
        if (!auctionItemRepository.findBlockedProductIdsIn(List.of(productId)).isEmpty()) {
            throw new ApplicationException(AuctionErrorType.PRODUCT_ALREADY_IN_AUCTION);
        }
    }
}
