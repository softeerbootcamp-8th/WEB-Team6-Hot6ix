package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemStartRequestDto;
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
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionItemServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final Long PRODUCT_ID = 20L;
    private static final Long ITEM_ID = 30L;

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private AuctionRoomRepository auctionRoomRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private AuctionItemService auctionItemService;

    private SellerProfile newSellerProfile() {
        User user = User.builder()
                .email("seller@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build();

        return SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .build();
    }

    private AuctionRoom newRoom(SellerProfile sellerProfile, AuctionRoomStatus status) {
        AuctionRoom auctionRoom = AuctionRoom.builder()
                .sellerProfile(sellerProfile)
                .name("승민의 경매방")
                .status(status)
                .bidIncrement(1_000L)
                .build();
        ReflectionTestUtils.setField(auctionRoom, "auctionRoomId", ROOM_ID);
        return auctionRoom;
    }

    private Product newProduct(SellerProfile sellerProfile) {
        Product product = Product.builder()
                .sellerProfile(sellerProfile)
                .name("한정판 피규어")
                .description("미개봉 정품")
                .imageUrl("https://cdn.hot6ix.com/item.png")
                .referenceUrl("https://instagram.com/hot6ix")
                .build();
        ReflectionTestUtils.setField(product, "productId", PRODUCT_ID);
        return product;
    }

    private AuctionItem newItem(AuctionRoom auctionRoom, Product product, AuctionItemStatus status) {
        AuctionItem auctionItem = AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(product)
                .startingPrice(50_000L)
                .bidIncrement(1_000L)
                .status(status)
                .build();
        ReflectionTestUtils.setField(auctionItem, "auctionItemId", ITEM_ID);
        return auctionItem;
    }

    private AuctionItemAddRequestDto newAddRequest() {
        return AuctionItemAddRequestDto.builder()
                .productId(PRODUCT_ID)
                .startingPrice(50_000L)
                .build();
    }

    private AuctionItemStartRequestDto newStartRequest(int durationMinutes) {
        return AuctionItemStartRequestDto.builder()
                .durationMinutes(durationMinutes)
                .build();
    }

    private TopBidderProjection row(Long itemId, int rankNo, String nickname, Long amount) {
        return new TopBidderProjection() {
            @Override public Long getAuctionItemId() { return itemId; }
            @Override public Integer getRankNo() { return rankNo; }
            @Override public String getNickname() { return nickname; }
            @Override public Long getAmount() { return amount; }
        };
    }

    private SellerProfile givenSellerProfile() {
        SellerProfile sellerProfile = newSellerProfile();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", 5L);

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(sellerProfile));

        return sellerProfile;
    }

    /** 시작 흐름의 프로필 → 물품 락 → 방 락까지 통과시키는 공통 스텁. */
    private AuctionItem givenLockedItem(AuctionRoomStatus roomStatus, AuctionItemStatus itemStatus) {
        SellerProfile sellerProfile = givenSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile, roomStatus);
        AuctionItem auctionItem = newItem(auctionRoom, newProduct(sellerProfile), itemStatus);

        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));
        when(auctionRoomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(auctionRoom));

        return auctionItem;
    }

    private void givenInProgressCount(long count) {
        when(auctionItemRepository.countByAuctionRoom_AuctionRoomIdAndStatus(
                ROOM_ID, AuctionItemStatus.IN_PROGRESS)).thenReturn(count);
    }

    /** 프로필 → 방 소유 확인까지 통과시키는 공통 스텁. */
    private SellerProfile givenOwnedRoom(AuctionRoomStatus status) {
        SellerProfile sellerProfile = newSellerProfile();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", 5L);

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, 5L)).thenReturn(Optional.of(newRoom(sellerProfile, status)));

        return sellerProfile;
    }

    @Test
    @DisplayName("물품이 없는 경매방은 예외 없이 빈 목록을 반환한다")
    void getSummariesReturnsEmptyList() {

        when(auctionRoomRepository.existsByAuctionRoomIdAndDeletedAtIsNull(1L)).thenReturn(true);
        when(auctionItemRepository.findSummaries(1L)).thenReturn(List.of());

        List<AuctionItemSummaryResponseDto> result = auctionItemService.getSummaries(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 경매방을 조회하면 AUCTION_ROOM_NOT_FOUND 예외가 발생한다")
    void getSummariesThrowsWhenRoomNotFound() {

        when(auctionRoomRepository.existsByAuctionRoomIdAndDeletedAtIsNull(999L)).thenReturn(false);

        assertThatThrownBy(() -> auctionItemService.getSummaries(999L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("상세 조회에 없는 물품이면 AUCTION_ITEM_NOT_FOUND 예외가 발생한다")
    void getDetailThrowsWhenNotFound() {

        when(auctionItemRepository.findDetail(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionItemService.getDetail(999L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("상세 조회는 낙찰된 물품도 반환한다")
    void getDetailReturnsSoldItem() {

        AuctionItemDetailResponseDto sold = new AuctionItemDetailResponseDto(
                1L,
                10L,
                "한정판 피규어",
                "미개봉 정품",
                "https://cdn.hot6ix.com/item.png",
                "https://instagram.com/hot6ix",
                10_000L,
                50_000L,
                1_000L,
                AuctionItemStatus.SOLD,
                LocalDateTime.of(2026, 7, 29, 21, 0));

        when(auctionItemRepository.findDetail(1L)).thenReturn(Optional.of(sold));

        AuctionItemDetailResponseDto result = auctionItemService.getDetail(1L);

        assertThat(result.status()).isEqualTo(AuctionItemStatus.SOLD);
    }

    @Test
    @DisplayName("상세 조회는 상위 3명을 순위대로 담는다")
    void getDetailFillsLeaderboard() {

        AuctionItemDetailResponseDto found = new AuctionItemDetailResponseDto(
                ITEM_ID, ROOM_ID, "한정판 피규어", "미개봉 정품",
                "https://cdn.hot6ix.com/item.png", "https://instagram.com/hot6ix",
                10_000L, 50_000L, 1_000L,
                AuctionItemStatus.IN_PROGRESS, LocalDateTime.of(2026, 7, 29, 21, 0));

        when(auctionItemRepository.findDetail(ITEM_ID)).thenReturn(Optional.of(found));
        when(bidRepository.findTopBidders(List.of(ITEM_ID), 3)).thenReturn(List.of(
                row(ITEM_ID, 1, "스니커홀릭", 50_000L),
                row(ITEM_ID, 2, "조던매니아", 48_000L),
                row(ITEM_ID, 3, "슈즈러버", 46_000L)));

        AuctionItemDetailResponseDto result = auctionItemService.getDetail(ITEM_ID);

        assertThat(result.leaderboard())
                .extracting(LeaderboardEntryResponseDto::rank)
                .containsExactly(1, 2, 3);
        assertThat(result.leaderboard())
                .extracting(LeaderboardEntryResponseDto::nickname)
                .containsExactly("스니커홀릭", "조던매니아", "슈즈러버");
        assertThat(result.leaderboard().get(0).amount()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("입찰이 없는 물품의 상세 조회는 빈 리더보드를 담는다")
    void getDetailFillsEmptyLeaderboardWhenNoBids() {

        AuctionItemDetailResponseDto found = new AuctionItemDetailResponseDto(
                ITEM_ID, ROOM_ID, "한정판 피규어", "미개봉 정품",
                "https://cdn.hot6ix.com/item.png", "https://instagram.com/hot6ix",
                10_000L, 10_000L, 1_000L,
                AuctionItemStatus.IN_PROGRESS, LocalDateTime.of(2026, 7, 29, 21, 0));

        when(auctionItemRepository.findDetail(ITEM_ID)).thenReturn(Optional.of(found));
        when(bidRepository.findTopBidders(List.of(ITEM_ID), 3)).thenReturn(List.of());

        AuctionItemDetailResponseDto result = auctionItemService.getDetail(ITEM_ID);

        assertThat(result.leaderboard()).isEmpty();
    }

    @Test
    @DisplayName("물품을 추가하면 READY 상태로 만들어지고 입찰 단위는 경매방 값을 복사한다")
    void add() {

        SellerProfile sellerProfile = givenOwnedRoom(AuctionRoomStatus.BEFORE);

        when(productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(PRODUCT_ID, 5L))
                .thenReturn(Optional.of(newProduct(sellerProfile)));
        when(auctionItemRepository.existsByProduct_ProductId(PRODUCT_ID)).thenReturn(false);
        when(auctionItemRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionItemDetailResponseDto response = auctionItemService.add(USER_ID, ROOM_ID, newAddRequest());

        assertThat(response.status()).isEqualTo(AuctionItemStatus.READY);
        assertThat(response.bidIncrement()).isEqualTo(1_000L);
        assertThat(response.productName()).isEqualTo("한정판 피규어");
        assertThat(response.auctionRoomId()).isEqualTo(ROOM_ID);
        assertThat(response.endAt()).isNull();
    }

    @Test
    @DisplayName("추가한 물품의 현재가는 시작가로 채워진다")
    void addSetsCurrentPriceToStartingPrice() {

        SellerProfile sellerProfile = givenOwnedRoom(AuctionRoomStatus.BEFORE);

        when(productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(PRODUCT_ID, 5L))
                .thenReturn(Optional.of(newProduct(sellerProfile)));
        when(auctionItemRepository.existsByProduct_ProductId(PRODUCT_ID)).thenReturn(false);
        when(auctionItemRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionItemDetailResponseDto response = auctionItemService.add(USER_ID, ROOM_ID, newAddRequest());

        assertThat(response.currentPrice()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("방송 중(OPEN)인 경매방에도 물품을 추가할 수 있다")
    void addAllowsOpenRoom() {

        SellerProfile sellerProfile = givenOwnedRoom(AuctionRoomStatus.OPEN);

        when(productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(PRODUCT_ID, 5L))
                .thenReturn(Optional.of(newProduct(sellerProfile)));
        when(auctionItemRepository.existsByProduct_ProductId(PRODUCT_ID)).thenReturn(false);
        when(auctionItemRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionItemDetailResponseDto response = auctionItemService.add(USER_ID, ROOM_ID, newAddRequest());

        assertThat(response.status()).isEqualTo(AuctionItemStatus.READY);
    }

    @Test
    @DisplayName("종료된 경매방에 물품을 추가하면 AUCTION_ROOM_CLOSED 예외가 발생한다")
    void addThrowsWhenRoomClosed() {

        givenOwnedRoom(AuctionRoomStatus.CLOSED);

        assertThatThrownBy(() -> auctionItemService.add(USER_ID, ROOM_ID, newAddRequest()))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_CLOSED);
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 추가 시 SELLER_PROFILE_NOT_FOUND 예외가 발생한다")
    void addThrowsWhenSellerProfileNotFound() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionItemService.add(USER_ID, ROOM_ID, newAddRequest()))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("경매방이 없거나 본인 소유가 아니면 추가 시 AUCTION_ROOM_NOT_FOUND 예외가 발생한다")
    void addThrowsWhenRoomNotOwned() {

        SellerProfile sellerProfile = newSellerProfile();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", 5L);

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionItemService.add(USER_ID, ROOM_ID, newAddRequest()))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("상품이 없거나 본인 소유가 아니면 추가 시 PRODUCT_NOT_FOUND 예외가 발생한다")
    void addThrowsWhenProductNotOwned() {

        givenOwnedRoom(AuctionRoomStatus.BEFORE);

        when(productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(PRODUCT_ID, 5L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionItemService.add(USER_ID, ROOM_ID, newAddRequest()))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 경매방에 올라간 상품을 추가하면 PRODUCT_ALREADY_IN_AUCTION 예외가 발생한다")
    void addThrowsWhenProductAlreadyInAuction() {

        SellerProfile sellerProfile = givenOwnedRoom(AuctionRoomStatus.BEFORE);

        when(productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(PRODUCT_ID, 5L))
                .thenReturn(Optional.of(newProduct(sellerProfile)));
        when(auctionItemRepository.existsByProduct_ProductId(PRODUCT_ID)).thenReturn(true);

        assertThatThrownBy(() -> auctionItemService.add(USER_ID, ROOM_ID, newAddRequest()))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.PRODUCT_ALREADY_IN_AUCTION);

        verify(auctionItemRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동시 요청으로 unique 제약에 걸려도 PRODUCT_ALREADY_IN_AUCTION 예외로 바뀐다")
    void addTranslatesUniqueViolation() {

        SellerProfile sellerProfile = givenOwnedRoom(AuctionRoomStatus.BEFORE);

        when(productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(PRODUCT_ID, 5L))
                .thenReturn(Optional.of(newProduct(sellerProfile)));
        when(auctionItemRepository.existsByProduct_ProductId(PRODUCT_ID)).thenReturn(false);
        when(auctionItemRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_auction_items_product_id"));

        assertThatThrownBy(() -> auctionItemService.add(USER_ID, ROOM_ID, newAddRequest()))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.PRODUCT_ALREADY_IN_AUCTION);
    }

    @Test
    @DisplayName("READY 물품을 빼면 물리 삭제된다")
    void remove() {

        SellerProfile sellerProfile = newSellerProfile();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", 5L);
        AuctionRoom auctionRoom = newRoom(sellerProfile, AuctionRoomStatus.BEFORE);
        AuctionItem auctionItem = newItem(auctionRoom, newProduct(sellerProfile), AuctionItemStatus.READY);

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, 5L)).thenReturn(true);
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));

        auctionItemService.remove(USER_ID, ROOM_ID, ITEM_ID);

        verify(auctionItemRepository).delete(auctionItem);
    }

    @Test
    @DisplayName("경매방이 없거나 본인 소유가 아니면 제외 시 AUCTION_ROOM_NOT_FOUND 예외가 발생한다")
    void removeThrowsWhenRoomNotOwned() {

        SellerProfile sellerProfile = newSellerProfile();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", 5L);

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, 5L)).thenReturn(false);

        assertThatThrownBy(() -> auctionItemService.remove(USER_ID, ROOM_ID, ITEM_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 물품을 빼면 AUCTION_ITEM_NOT_FOUND 예외가 발생한다")
    void removeThrowsWhenItemNotFound() {

        SellerProfile sellerProfile = newSellerProfile();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", 5L);

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, 5L)).thenReturn(true);
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionItemService.remove(USER_ID, ROOM_ID, ITEM_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 경매방에 속한 물품을 빼면 AUCTION_ITEM_NOT_FOUND 예외가 발생한다")
    void removeThrowsWhenItemBelongsToAnotherRoom() {

        SellerProfile sellerProfile = newSellerProfile();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", 5L);

        AuctionRoom otherRoom = newRoom(sellerProfile, AuctionRoomStatus.BEFORE);
        ReflectionTestUtils.setField(otherRoom, "auctionRoomId", 999L);
        AuctionItem auctionItem = newItem(otherRoom, newProduct(sellerProfile), AuctionItemStatus.READY);

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, 5L)).thenReturn(true);
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));

        assertThatThrownBy(() -> auctionItemService.remove(USER_ID, ROOM_ID, ITEM_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);

        verify(auctionItemRepository, never()).delete(any());
    }

    @Test
    @DisplayName("이미 시작된 물품을 빼면 AUCTION_ITEM_ALREADY_STARTED 예외가 발생한다")
    void removeThrowsWhenItemAlreadyStarted() {

        SellerProfile sellerProfile = newSellerProfile();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", 5L);
        AuctionRoom auctionRoom = newRoom(sellerProfile, AuctionRoomStatus.OPEN);
        AuctionItem auctionItem = newItem(auctionRoom, newProduct(sellerProfile), AuctionItemStatus.IN_PROGRESS);

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, 5L)).thenReturn(true);
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));

        assertThatThrownBy(() -> auctionItemService.remove(USER_ID, ROOM_ID, ITEM_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_ALREADY_STARTED);

        verify(auctionItemRepository, never()).delete(any());
    }

    @Test
    @DisplayName("대기 중인 물품을 시작하면 진행중으로 바뀌고 마감 시각이 계산된다")
    void startTransitionsItemAndComputesEndAt() {

        AuctionItem auctionItem = givenLockedItem(AuctionRoomStatus.OPEN, AuctionItemStatus.READY);
        givenInProgressCount(0L);

        LocalDateTime before = LocalDateTime.now();
        AuctionItemDetailResponseDto response =
                auctionItemService.start(ITEM_ID, USER_ID, newStartRequest(30));
        LocalDateTime after = LocalDateTime.now();

        assertThat(auctionItem.getStatus()).isEqualTo(AuctionItemStatus.IN_PROGRESS);
        assertThat(auctionItem.getStartedAt()).isBetween(before, after);
        assertThat(auctionItem.getOriginalEndAt()).isEqualTo(auctionItem.getStartedAt().plusMinutes(30));
        assertThat(auctionItem.getEndAt()).isEqualTo(auctionItem.getOriginalEndAt());
        assertThat(auctionItem.getTotalExtensionSeconds()).isZero();

        assertThat(response.status()).isEqualTo(AuctionItemStatus.IN_PROGRESS);
        assertThat(response.endAt()).isEqualTo(auctionItem.getEndAt());
    }

    @Test
    @DisplayName("물품을 시작하면 ItemStarted 이벤트가 발행된다")
    void startPublishesItemStarted() {

        AuctionItem auctionItem = givenLockedItem(AuctionRoomStatus.OPEN, AuctionItemStatus.READY);
        givenInProgressCount(0L);

        auctionItemService.start(ITEM_ID, USER_ID, newStartRequest(30));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher).publish(captor.capture());

        assertThat(captor.getValue()).isInstanceOfSatisfying(ItemStarted.class, event -> {
            assertThat(event.roomId()).isEqualTo(ROOM_ID);
            assertThat(event.itemId()).isEqualTo(ITEM_ID);
            assertThat(event.itemName()).isEqualTo("한정판 피규어");
            assertThat(event.occurredAt()).isEqualTo(auctionItem.getStartedAt());
            assertThat(event.endAt())
                    .as("구독자가 재조회 없이 카운트다운을 그릴 수 있어야 한다")
                    .isEqualTo(auctionItem.getEndAt());
        });
    }

    @Test
    @DisplayName("시작 전 경매방에서 물품을 시작하면 경매방이 방송 중으로 바뀐다")
    void startOpensRoomWhenBefore() {

        AuctionItem auctionItem = givenLockedItem(AuctionRoomStatus.BEFORE, AuctionItemStatus.READY);
        givenInProgressCount(0L);

        auctionItemService.start(ITEM_ID, USER_ID, newStartRequest(30));

        assertThat(auctionItem.getAuctionRoom().getStatus()).isEqualTo(AuctionRoomStatus.OPEN);
    }

    @Test
    @DisplayName("이미 진행 중인 물품이 2개면 3번째 물품은 시작된다")
    void startSucceedsWhenBelowLimit() {

        AuctionItem auctionItem = givenLockedItem(AuctionRoomStatus.OPEN, AuctionItemStatus.READY);
        givenInProgressCount(2L);

        auctionItemService.start(ITEM_ID, USER_ID, newStartRequest(30));

        assertThat(auctionItem.getStatus()).isEqualTo(AuctionItemStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("이미 진행 중인 물품이 3개면 AUCTION_ITEM_START_LIMIT_EXCEEDED 예외가 발생한다")
    void startThrowsWhenLimitExceeded() {

        AuctionItem auctionItem = givenLockedItem(AuctionRoomStatus.OPEN, AuctionItemStatus.READY);
        givenInProgressCount(3L);

        assertThatThrownBy(() -> auctionItemService.start(ITEM_ID, USER_ID, newStartRequest(30)))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_START_LIMIT_EXCEEDED);

        assertThat(auctionItem.getStatus()).isEqualTo(AuctionItemStatus.READY);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 SELLER_PROFILE_NOT_FOUND 예외가 발생한다")
    void startThrowsWhenSellerProfileNotFound() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionItemService.start(ITEM_ID, USER_ID, newStartRequest(30)))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 물품을 시작하면 AUCTION_ITEM_NOT_FOUND 예외가 발생한다")
    void startThrowsWhenItemNotFound() {

        givenSellerProfile();
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionItemService.start(ITEM_ID, USER_ID, newStartRequest(30)))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 경매방의 물품을 시작하면 AUCTION_ITEM_NOT_FOUND 예외가 발생한다")
    void startThrowsWhenRoomSoftDeleted() {

        SellerProfile sellerProfile = givenSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile, AuctionRoomStatus.OPEN);
        AuctionItem auctionItem = newItem(auctionRoom, newProduct(sellerProfile), AuctionItemStatus.READY);

        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));
        when(auctionRoomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionItemService.start(ITEM_ID, USER_ID, newStartRequest(30)))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 경매방 물품을 시작하면 AUCTION_ITEM_NOT_FOUND 예외가 발생한다")
    void startThrowsWhenNotOwner() {

        givenSellerProfile();

        SellerProfile otherSeller = newSellerProfile();
        ReflectionTestUtils.setField(otherSeller, "sellerProfileId", 6L);
        AuctionRoom otherRoom = newRoom(otherSeller, AuctionRoomStatus.OPEN);
        AuctionItem auctionItem = newItem(otherRoom, newProduct(otherSeller), AuctionItemStatus.READY);

        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));
        when(auctionRoomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(otherRoom));

        assertThatThrownBy(() -> auctionItemService.start(ITEM_ID, USER_ID, newStartRequest(30)))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);

        assertThat(auctionItem.getStatus()).isEqualTo(AuctionItemStatus.READY);
    }

    @Test
    @DisplayName("종료된 경매방의 물품을 시작하면 AUCTION_ROOM_CLOSED 예외가 발생한다")
    void startThrowsWhenRoomClosed() {

        AuctionItem auctionItem = givenLockedItem(AuctionRoomStatus.CLOSED, AuctionItemStatus.READY);

        assertThatThrownBy(() -> auctionItemService.start(ITEM_ID, USER_ID, newStartRequest(30)))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_CLOSED);

        assertThat(auctionItem.getStatus()).isEqualTo(AuctionItemStatus.READY);
    }

    @ParameterizedTest
    @EnumSource(value = AuctionItemStatus.class, names = {"IN_PROGRESS", "SOLD", "FAILED"})
    @DisplayName("대기 중이 아닌 물품을 시작하면 AUCTION_ITEM_NOT_READY 예외가 발생한다")
    void startThrowsWhenItemNotReady(AuctionItemStatus status) {

        givenLockedItem(AuctionRoomStatus.OPEN, status);

        assertThatThrownBy(() -> auctionItemService.start(ITEM_ID, USER_ID, newStartRequest(30)))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_READY);

        verify(domainEventPublisher, never()).publish(any());
    }
}
