package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomCountsResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomListItemResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResultResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomRole;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemResultProjection;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.domain.deal.repository.MyCandidateRankProjection;
import com.hot6ix.upbid.domain.sse.service.RoomSseManager;
import com.hot6ix.upbid.domain.upload.ImageUrlValidator;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.RoomUpdated;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
import com.hot6ix.upbid.global.response.CursorPageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionRoomServiceTest {

    @Mock
    private AuctionRoomRepository auctionRoomRepository;

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    @Mock
    private AuctionRoomShareService auctionRoomShareService;

    @Mock
    private DealCandidateRepository dealCandidateRepository;

    @Mock
    private RoomSseManager roomSseManager;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    // 이미지 주소 검증은 ImageUrlValidatorTest에서 본다. 여기서는 통과시킨다.
    // 빼면 @InjectMocks가 null을 꽂아서 create·update가 NPE로 죽는다.
    @Mock
    private ImageUrlValidator imageUrlValidator;

    @InjectMocks
    private AuctionRoomService auctionRoomService;

    /** 방을 만든 판매자의 회원 ID. isOwner 판정이 이 값과 조회자를 비교한다. */
    private static final Long SELLER_USER_ID = 1L;

    /** 공개 조회는 숫자 PK를 받지 않는다. 방을 지목하는 값은 항상 이 공유 코드다. */
    private static final String SHARE_CODE = "aBcD1234aBcD1234";

    private SellerProfile newSellerProfile() {
        User user = User.builder()
                .email("seller@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build();
        ReflectionTestUtils.setField(user, "userId", SELLER_USER_ID);

        return SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .build();
    }

    private AuctionRoomCreateRequestDto newCreateRequest() {
        return AuctionRoomCreateRequestDto.builder()
                .bidIncrement(1_000L)
                .name("승민의 경매방")
                .coverImageUrl("https://cdn.hot6ix.com/cover.png")
                .description("한정판 피규어 경매")
                .liveUrl("https://instagram.com/hot6ix")
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();
    }

    @Test
    @DisplayName("경매방을 생성하면 BEFORE 상태로 share_code와 함께 저장된다")
    void create() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoomCreateRequestDto request = newCreateRequest();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sellerProfile));
        when(auctionRoomShareService.generateCandidateShareCode()).thenReturn("FAKESHARECODE1234");
        when(auctionRoomRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionRoomPublicResponseDto response = auctionRoomService.create(1L, request);

        assertThat(response.name()).isEqualTo("승민의 경매방");
        assertThat(response.status()).isEqualTo(AuctionRoomStatus.BEFORE);
        assertThat(response.bidIncrement()).isEqualTo(1_000L);
        assertThat(response.sellerStoreName()).isEqualTo("승민상점");
        assertThat(response.itemCount()).isZero();
        assertThat(response.participantCount()).isNull();
        verify(auctionRoomRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("share_code가 충돌하면 재시도해서 생성에 성공한다")
    void create_retriesOnShareCodeCollision() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoomCreateRequestDto request = newCreateRequest();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sellerProfile));
        when(auctionRoomShareService.generateCandidateShareCode()).thenReturn("FAKESHARECODE1234");
        when(auctionRoomRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("share_code unique constraint violated"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuctionRoomPublicResponseDto response = auctionRoomService.create(1L, request);

        assertThat(response.name()).isEqualTo("승민의 경매방");
        verify(auctionRoomRepository, times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName("share_code 충돌이 반복해서 재시도 한도를 넘으면 예외가 발생한다")
    void create_exhaustsShareCodeRetries() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoomCreateRequestDto request = newCreateRequest();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sellerProfile));
        when(auctionRoomShareService.generateCandidateShareCode()).thenReturn("FAKESHARECODE1234");
        when(auctionRoomRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("share_code unique constraint violated"));

        assertThatThrownBy(() -> auctionRoomService.create(1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(CommonErrorType.INTERNAL_SERVER_ERROR);

        verify(auctionRoomRepository, times(5)).saveAndFlush(any());
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 생성 시 예외가 발생한다")
    void create_sellerProfileNotFound() {

        AuctionRoomCreateRequestDto request = newCreateRequest();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionRoomService.create(1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);

        verify(auctionRoomRepository, times(0)).saveAndFlush(any());
    }

    @Test
    @DisplayName("다른 회원이나 게스트가 조회하면 isOwner가 false다")
    void getRoomByShareCode_notOwner() {

        AuctionRoom auctionRoom = AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(newSellerProfile())
                .name("승민의 경매방")
                .shareCode("aBcD1234aBcD1234")
                .status(AuctionRoomStatus.BEFORE)
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();

        when(auctionRoomRepository.findByShareCodeAndDeletedAtIsNull("aBcD1234aBcD1234"))
                .thenReturn(Optional.of(auctionRoom));

        assertThat(auctionRoomService.getRoomByShareCode("aBcD1234aBcD1234", SELLER_USER_ID + 1)
                .isOwner()).isFalse();
        assertThat(auctionRoomService.getRoomByShareCode("aBcD1234aBcD1234", null)
                .isOwner()).isFalse();
    }

    @Test
    @DisplayName("공유 코드로 경매방 공개 정보를 조회한다")
    void getRoomByShareCode() {

        AuctionRoom auctionRoom = AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(newSellerProfile())
                .name("승민의 경매방")
                .shareCode("aBcD1234aBcD1234")
                .status(AuctionRoomStatus.BEFORE)
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();
        ReflectionTestUtils.setField(auctionRoom, "auctionRoomId", 10L);

        when(auctionRoomRepository.findByShareCodeAndDeletedAtIsNull("aBcD1234aBcD1234"))
                .thenReturn(Optional.of(auctionRoom));
        when(auctionItemRepository.countByAuctionRoom_AuctionRoomId(10L)).thenReturn(3L);

        AuctionRoomPublicResponseDto response =
                auctionRoomService.getRoomByShareCode("aBcD1234aBcD1234", SELLER_USER_ID);

        assertThat(response.auctionRoomId()).isEqualTo(10L);
        // 소유자 전용 API는 여전히 숫자 ID를 받으므로 화면이 두 식별자를 함께 들고 있어야 한다.
        assertThat(response.shareCode()).isEqualTo("aBcD1234aBcD1234");
        assertThat(response.name()).isEqualTo("승민의 경매방");
        assertThat(response.status()).isEqualTo(AuctionRoomStatus.BEFORE);
        assertThat(response.sellerStoreName()).isEqualTo("승민상점");
        assertThat(response.itemCount()).isEqualTo(3L);
        assertThat(response.isOwner()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 공유 코드로 조회하면 예외가 발생한다")
    void getRoomByShareCode_notFound() {

        when(auctionRoomRepository.findByShareCodeAndDeletedAtIsNull("unknownShareCode"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionRoomService.getRoomByShareCode("unknownShareCode", SELLER_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("커버 주소가 우리 버킷에서 온 것인지 생성할 때 확인한다")
    void create_validatesCoverImageUrl() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoomCreateRequestDto request = newCreateRequest();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sellerProfile));
        when(auctionRoomShareService.generateCandidateShareCode()).thenReturn("FAKESHARECODE1234");
        when(auctionRoomRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        auctionRoomService.create(1L, request);

        verify(imageUrlValidator, times(1)).validate("https://cdn.hot6ix.com/cover.png");
    }

    @Test
    @DisplayName("커버 주소는 수정할 때도 확인한다 — 생성만 막으면 검증이 없는 것과 같다")
    void update_validatesCoverImageUrl() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newUpdatableRoom(sellerProfile, AuctionRoomStatus.BEFORE);
        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .coverImageUrl("https://cdn.hot6ix.com/new-cover.png")
                .build();

        stubOwnedRoom(sellerProfile, auctionRoom);
        when(auctionItemRepository.existsByAuctionRoom_AuctionRoomIdAndStatusNot(10L, AuctionItemStatus.READY))
                .thenReturn(false);

        auctionRoomService.update(1L, 10L, request);

        verify(imageUrlValidator, times(1)).validate("https://cdn.hot6ix.com/new-cover.png");
    }

    @Test
    @DisplayName("물품이 하나도 시작되지 않았으면 경매방 설정을 전부 수정할 수 있다")
    void update_succeedsWhenNoItemStarted() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newUpdatableRoom(sellerProfile, AuctionRoomStatus.BEFORE);
        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("새로운 경매방 이름")
                .description("새로운 소개")
                .build();

        stubOwnedRoom(sellerProfile, auctionRoom);
        when(auctionItemRepository.existsByAuctionRoom_AuctionRoomIdAndStatusNot(10L, AuctionItemStatus.READY))
                .thenReturn(false);

        AuctionRoomPublicResponseDto response = auctionRoomService.update(1L, 10L, request);

        assertThat(response.name()).isEqualTo("새로운 경매방 이름");
        assertThat(auctionRoom.getName()).isEqualTo("새로운 경매방 이름");
        assertThat(auctionRoom.getDescription()).isEqualTo("새로운 소개");
    }

    @Test
    @DisplayName("물품 중 하나라도 시작된 적 있으면 이름·방송 링크 밖의 설정 수정 시 예외가 발생한다")
    void update_throwsWhenItemAlreadyStarted() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newUpdatableRoom(sellerProfile, AuctionRoomStatus.OPEN);
        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("새로운 경매방 이름")
                .description("새로운 소개")
                .build();

        stubOwnedRoom(sellerProfile, auctionRoom);
        when(auctionItemRepository.existsByAuctionRoom_AuctionRoomIdAndStatusNot(10L, AuctionItemStatus.READY))
                .thenReturn(true);

        assertThatThrownBy(() -> auctionRoomService.update(1L, 10L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_ALREADY_STARTED);

        assertThat(auctionRoom.getName()).isEqualTo("승민의 경매방");
    }

    /**
     * 방송 링크는 방송을 켜고 "안 열려요"라는 말을 듣고서야 잘못이 드러난다. 시작과 함께
     * 잠그면 정작 고쳐야 할 순간에 못 고치므로, 이름과 함께 진행 중에도 열어 둔다.
     */
    @Test
    @DisplayName("방송 링크는 물품이 시작된 뒤에도 바꾸거나 지울 수 있다")
    void update_liveUrlSucceedsAfterStart() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newUpdatableRoom(sellerProfile, AuctionRoomStatus.OPEN);

        stubOwnedRoom(sellerProfile, auctionRoom);

        auctionRoomService.update(1L, 10L, AuctionRoomUpdateRequestDto.builder()
                .liveUrl("https://youtube.com/@fixed")
                .build());
        assertThat(auctionRoom.getLiveUrl()).isEqualTo("https://youtube.com/@fixed");

        auctionRoomService.update(1L, 10L, AuctionRoomUpdateRequestDto.builder()
                .liveUrl("")
                .build());
        assertThat(auctionRoom.getLiveUrl()).isNull();

        // 시작 여부를 아예 묻지 않는다 — 방송 링크는 진행 중에도 열려 있어서다.
        verify(auctionItemRepository, never())
                .existsByAuctionRoom_AuctionRoomIdAndStatusNot(any(), any());
    }

    @Test
    @DisplayName("이름만 바꾸는 요청은 물품이 시작된 뒤에도 통과한다")
    void update_nameOnlySucceedsAfterStart() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newUpdatableRoom(sellerProfile, AuctionRoomStatus.OPEN);
        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("오타를 고친 이름")
                .build();

        stubOwnedRoom(sellerProfile, auctionRoom);

        AuctionRoomPublicResponseDto response = auctionRoomService.update(1L, 10L, request);

        assertThat(response.name()).isEqualTo("오타를 고친 이름");
        // 시작 여부를 아예 묻지 않는다 — 이름은 진행 중에도 열려 있어서다.
        verify(auctionItemRepository, never())
                .existsByAuctionRoom_AuctionRoomIdAndStatusNot(any(), any());
    }

    @Test
    @DisplayName("설정을 수정하면 RoomUpdated를 발행해 이미 들어와 있는 사람도 방 정보를 다시 읽게 한다")
    void update_publishesRoomUpdated() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newUpdatableRoom(sellerProfile, AuctionRoomStatus.OPEN);
        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("오타를 고친 이름")
                .build();

        stubOwnedRoom(sellerProfile, auctionRoom);

        auctionRoomService.update(1L, 10L, request);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher).publish(captor.capture());

        assertThat(captor.getValue()).isInstanceOfSatisfying(RoomUpdated.class, event ->
                assertThat(event.roomId()).isEqualTo(10L));
    }

    @Test
    @DisplayName("수정이 거절되면 RoomUpdated를 발행하지 않는다")
    void update_doesNotPublishWhenRejected() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newUpdatableRoom(sellerProfile, AuctionRoomStatus.CLOSED);
        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("새 이름")
                .build();

        stubOwnedRoom(sellerProfile, auctionRoom);

        assertThatThrownBy(() -> auctionRoomService.update(1L, 10L, request))
                .isInstanceOf(ApplicationException.class);

        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("종료된 경매방은 이름만 바꾸려 해도 예외가 발생한다")
    void update_throwsWhenRoomClosed() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newUpdatableRoom(sellerProfile, AuctionRoomStatus.CLOSED);
        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("새로운 경매방 이름")
                .build();

        stubOwnedRoom(sellerProfile, auctionRoom);

        assertThatThrownBy(() -> auctionRoomService.update(1L, 10L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_CLOSED);

        assertThat(auctionRoom.getName()).isEqualTo("승민의 경매방");
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 수정 시 예외가 발생한다")
    void update_sellerProfileNotFound() {

        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder().name("새 이름").build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionRoomService.update(1L, 10L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("경매방이 없거나 본인 소유가 아니면 수정 시 예외가 발생한다")
    void update_roomNotFoundOrNotOwned() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder().name("새 이름").build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                10L, sellerProfile.getSellerProfileId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionRoomService.update(1L, 10L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }

    private AuctionRoom newUpdatableRoom(SellerProfile sellerProfile, AuctionRoomStatus status) {
        return AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name("승민의 경매방")
                .status(status)
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();
    }

    private void stubOwnedRoom(SellerProfile sellerProfile, AuctionRoom auctionRoom) {
        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                10L, sellerProfile.getSellerProfileId())).thenReturn(Optional.of(auctionRoom));
    }

    private AuctionRoomListItemResponseDto newListItem(Long auctionRoomId, AuctionRoomStatus status) {
        return AuctionRoomListItemResponseDto.builder()
                .auctionRoomId(auctionRoomId)
                .name("승민의 경매방")
                .status(status)
                .role(AuctionRoomRole.SELLER)
                .storeName("승민상점")
                .createdAt(LocalDateTime.of(2026, 8, 3, 12, 0))
                .itemCount(2L)
                .build();
    }

    @Test
    @DisplayName("내 경매방 목록을 조회하면 요청한 쪽 크기만큼만 담기고 다음 커서가 채워진다")
    void getMyRooms_hasNext() {

        // size 2를 요청하면 리포지토리는 hasNext 판정용으로 3건을 돌려준다.
        when(auctionRoomRepository.search(1L, null, null, null, null, 2))
                .thenReturn(List.of(
                        newListItem(30L, AuctionRoomStatus.BEFORE),
                        newListItem(20L, AuctionRoomStatus.BEFORE),
                        newListItem(10L, AuctionRoomStatus.BEFORE)));

        CursorPageResponse<AuctionRoomListItemResponseDto> response =
                auctionRoomService.getMyRooms(1L, null, null, null, null, 2);

        assertThat(response.content()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(20L);
    }

    @Test
    @DisplayName("마지막 쪽이면 다음 커서가 비어 있다")
    void getMyRooms_lastPage() {

        when(auctionRoomRepository.search(1L, null, null, null, null, 2))
                .thenReturn(List.of(newListItem(30L, AuctionRoomStatus.BEFORE)));

        CursorPageResponse<AuctionRoomListItemResponseDto> response =
                auctionRoomService.getMyRooms(1L, null, null, null, null, 2);

        assertThat(response.content()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("쪽 크기를 주지 않으면 기본값으로 조회한다")
    void getMyRooms_defaultPageSize() {

        when(auctionRoomRepository.search(1L, null, null, null, null, AuctionRoomRepository.DEFAULT_PAGE_SIZE))
                .thenReturn(List.of());

        CursorPageResponse<AuctionRoomListItemResponseDto> response =
                auctionRoomService.getMyRooms(1L, null, null, null, null, null);

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("판매자 프로필이 없어도 목록이 조회된다")
    void getMyRooms_worksWithoutSellerProfile() {

        when(auctionRoomRepository.search(1L, null, null, null, null, 2))
                .thenReturn(List.of(newListItem(30L, AuctionRoomStatus.BEFORE)));

        CursorPageResponse<AuctionRoomListItemResponseDto> response =
                auctionRoomService.getMyRooms(1L, null, null, null, null, 2);

        assertThat(response.content()).hasSize(1);
        verifyNoInteractions(sellerProfileRepository);
    }

    @Test
    @DisplayName("OPEN인 방만 지금 접속 중인 참여자 수가 채워진다")
    void getMyRooms_fillsParticipantCountForOpenRooms() {

        when(auctionRoomRepository.search(1L, null, null, null, null, 2))
                .thenReturn(List.of(
                        newListItem(30L, AuctionRoomStatus.OPEN),
                        newListItem(20L, AuctionRoomStatus.CLOSED)));
        when(roomSseManager.getParticipantCount(30L)).thenReturn(12);

        CursorPageResponse<AuctionRoomListItemResponseDto> response =
                auctionRoomService.getMyRooms(1L, null, null, null, null, 2);

        assertThat(response.content().get(0).participantCount()).isEqualTo(12L);
        assertThat(response.content().get(1).participantCount()).isNull();
        verify(roomSseManager, never()).getParticipantCount(20L);
    }

    @Test
    @DisplayName("상태별 개수는 role을 문자열로 넘겨 조회한다")
    void getMyRoomCounts_passesRoleAsString() {

        when(auctionRoomRepository.countByStatus(1L, "포토카드", "SELLER"))
                .thenReturn(new AuctionRoomCountsResponseDto(1L, 2L, 3L));

        AuctionRoomCountsResponseDto counts =
                auctionRoomService.getMyRoomCounts(1L, "포토카드", AuctionRoomRole.SELLER);

        assertThat(counts.open()).isEqualTo(2L);
    }

    @Test
    @DisplayName("role이 없으면 개수 조회에도 null로 넘어간다")
    void getMyRoomCounts_passesNullRole() {

        when(auctionRoomRepository.countByStatus(1L, null, null))
                .thenReturn(new AuctionRoomCountsResponseDto(0L, 0L, 0L));

        AuctionRoomCountsResponseDto counts = auctionRoomService.getMyRoomCounts(1L, null, null);

        assertThat(counts.before()).isZero();
    }

    private AuctionRoom newClosedRoom() {
        AuctionRoom auctionRoom = AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(newSellerProfile())
                .name("승민의 경매방")
                .shareCode(SHARE_CODE)
                .status(AuctionRoomStatus.CLOSED)
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();
        // 결과 조회는 공유 코드로 찾은 방에서 ID를 꺼내 물품을 읽는다. 없으면 조회가 빈손이 된다.
        ReflectionTestUtils.setField(auctionRoom, "auctionRoomId", 10L);

        return auctionRoom;
    }

    /** 낙찰 1건 + 유찰 1건. 결과 화면이 둘을 나눠 세는 최소 조합이다. */
    private List<AuctionItemResultProjection> newResultRows() {
        return List.of(
                new AuctionItemResultProjection(
                        101L, "한정판 피규어", "https://cdn.hot6ix.com/1.png",
                        AuctionItemStatus.SOLD, 85_000L, "스니커홀릭"),
                new AuctionItemResultProjection(
                        102L, "빈티지 자켓", null,
                        AuctionItemStatus.FAILED, 30_000L, null));
    }

    @Test
    @DisplayName("낙찰 물품은 낙찰가와 낙찰자가, 유찰 물품은 둘 다 비어서 조회된다")
    void getResults() {

        when(auctionRoomRepository.findByShareCodeAndDeletedAtIsNull(SHARE_CODE))
                .thenReturn(Optional.of(newClosedRoom()));
        when(auctionItemRepository.findResults(10L)).thenReturn(newResultRows());
        when(dealCandidateRepository.findMyRanksInRoom(10L, 1L)).thenReturn(List.of());

        AuctionRoomResultResponseDto response = auctionRoomService.getResults(SHARE_CODE, 1L);

        assertThat(response.name()).isEqualTo("승민의 경매방");
        assertThat(response.sellerStoreName()).isEqualTo("승민상점");
        assertThat(response.status()).isEqualTo(AuctionRoomStatus.CLOSED);
        assertThat(response.items()).hasSize(2);

        assertThat(response.items().getFirst().finalPrice()).isEqualTo(85_000L);
        assertThat(response.items().getFirst().winnerNickname()).isEqualTo("스니커홀릭");

        // 유찰 물품의 currentPrice(30,000)는 아무도 부르지 않은 시작가라 가격으로 내리지 않는다.
        assertThat(response.items().getLast().finalPrice()).isNull();
        assertThat(response.items().getLast().winnerNickname()).isNull();
    }

    @Test
    @DisplayName("진행 중인 물품은 최고 입찰자가 있어도 낙찰자로 내려가지 않는다")
    void getResults_inProgressItemHasNoWinner() {

        when(auctionRoomRepository.findByShareCodeAndDeletedAtIsNull(SHARE_CODE))
                .thenReturn(Optional.of(newClosedRoom()));
        when(auctionItemRepository.findResults(10L)).thenReturn(List.of(
                new AuctionItemResultProjection(
                        103L, "진행 중인 물품", null,
                        AuctionItemStatus.IN_PROGRESS, 50_000L, "지금1위")));
        when(dealCandidateRepository.findMyRanksInRoom(10L, 1L)).thenReturn(List.of());

        AuctionRoomResultResponseDto response = auctionRoomService.getResults(SHARE_CODE, 1L);

        assertThat(response.items().getFirst().status()).isEqualTo(AuctionItemStatus.IN_PROGRESS);
        assertThat(response.items().getFirst().winnerNickname()).isNull();
        assertThat(response.items().getFirst().finalPrice()).isNull();
    }

    @Test
    @DisplayName("후보로 오른 물품에만 내 순위가 담긴다")
    void getResults_fillsMyRankOnlyWhereCandidate() {

        when(auctionRoomRepository.findByShareCodeAndDeletedAtIsNull(SHARE_CODE))
                .thenReturn(Optional.of(newClosedRoom()));
        when(auctionItemRepository.findResults(10L)).thenReturn(newResultRows());
        when(dealCandidateRepository.findMyRanksInRoom(10L, 1L))
                .thenReturn(List.of(new MyCandidateRankProjection(101L, 7, 60_000L)));

        AuctionRoomResultResponseDto response = auctionRoomService.getResults(SHARE_CODE, 1L);

        assertThat(response.items().getFirst().myRank()).isEqualTo(7);
        assertThat(response.items().getFirst().myAmount()).isEqualTo(60_000L);
        assertThat(response.items().getLast().myRank()).isNull();
        assertThat(response.items().getLast().myAmount()).isNull();
    }

    @Test
    @DisplayName("비로그인 요청은 내 순위를 조회하지 않고 전부 비운다")
    void getResults_guestHasNoMyRank() {

        when(auctionRoomRepository.findByShareCodeAndDeletedAtIsNull(SHARE_CODE))
                .thenReturn(Optional.of(newClosedRoom()));
        when(auctionItemRepository.findResults(10L)).thenReturn(newResultRows());

        AuctionRoomResultResponseDto response = auctionRoomService.getResults(SHARE_CODE, null);

        assertThat(response.items())
                .allSatisfy(item -> {
                    assertThat(item.myRank()).isNull();
                    assertThat(item.myAmount()).isNull();
                });
        verify(dealCandidateRepository, never()).findMyRanksInRoom(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 공유 코드의 결과를 조회하면 예외가 발생한다")
    void getResults_notFound() {

        when(auctionRoomRepository.findByShareCodeAndDeletedAtIsNull("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionRoomService.getResults("nope", 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }
}
