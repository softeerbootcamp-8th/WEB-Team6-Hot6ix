package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomListItemResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
import com.hot6ix.upbid.global.response.CursorPageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @InjectMocks
    private AuctionRoomService auctionRoomService;

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

    private AuctionRoomCreateRequestDto newCreateRequest() {
        return AuctionRoomCreateRequestDto.builder()
                .bidIncrement(1_000L)
                .name("승민의 경매방")
                .coverImageUrl("https://cdn.hot6ix.com/cover.png")
                .description("한정판 피규어 경매")
                .liveUrl("https://instagram.com/hot6ix")
                .softCloseTriggerSeconds(30)
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
    @DisplayName("경매방 공개 정보를 조회한다")
    void getRoom() {

        AuctionRoom auctionRoom = AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(newSellerProfile())
                .name("승민의 경매방")
                .status(AuctionRoomStatus.BEFORE)
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();

        when(auctionRoomRepository.findByAuctionRoomIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(auctionRoom));
        when(auctionItemRepository.countByAuctionRoom_AuctionRoomId(10L)).thenReturn(3L);

        AuctionRoomPublicResponseDto response = auctionRoomService.getRoom(10L);

        assertThat(response.name()).isEqualTo("승민의 경매방");
        assertThat(response.status()).isEqualTo(AuctionRoomStatus.BEFORE);
        assertThat(response.sellerStoreName()).isEqualTo("승민상점");
        assertThat(response.itemCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("존재하지 않거나 삭제된 경매방을 조회하면 예외가 발생한다")
    void getRoom_notFound() {

        when(auctionRoomRepository.findByAuctionRoomIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionRoomService.getRoom(999L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
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
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();
        ReflectionTestUtils.setField(auctionRoom, "auctionRoomId", 10L);

        when(auctionRoomRepository.findByShareCodeAndDeletedAtIsNull("aBcD1234aBcD1234"))
                .thenReturn(Optional.of(auctionRoom));
        when(auctionItemRepository.countByAuctionRoom_AuctionRoomId(10L)).thenReturn(3L);

        AuctionRoomPublicResponseDto response = auctionRoomService.getRoomByShareCode("aBcD1234aBcD1234");

        assertThat(response.auctionRoomId()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("승민의 경매방");
        assertThat(response.itemCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("존재하지 않는 공유 코드로 조회하면 예외가 발생한다")
    void getRoomByShareCode_notFound() {

        when(auctionRoomRepository.findByShareCodeAndDeletedAtIsNull("unknownShareCode"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionRoomService.getRoomByShareCode("unknownShareCode"))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("물품이 하나도 시작되지 않았으면 경매방 설정을 수정할 수 있다")
    void update_succeedsWhenNoItemStarted() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name("승민의 경매방")
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();
        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("새로운 경매방 이름")
                .build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                10L, sellerProfile.getSellerProfileId())).thenReturn(Optional.of(auctionRoom));
        when(auctionItemRepository.existsByAuctionRoom_AuctionRoomIdAndStatusNot(10L, AuctionItemStatus.READY))
                .thenReturn(false);

        AuctionRoomPublicResponseDto response = auctionRoomService.update(1L, 10L, request);

        assertThat(response.name()).isEqualTo("새로운 경매방 이름");
        assertThat(auctionRoom.getName()).isEqualTo("새로운 경매방 이름");
    }

    @Test
    @DisplayName("물품 중 하나라도 시작된 적 있으면 경매방 설정 수정 시 예외가 발생한다")
    void update_throwsWhenItemAlreadyStarted() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name("승민의 경매방")
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();
        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("새로운 경매방 이름")
                .build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                10L, sellerProfile.getSellerProfileId())).thenReturn(Optional.of(auctionRoom));
        when(auctionItemRepository.existsByAuctionRoom_AuctionRoomIdAndStatusNot(10L, AuctionItemStatus.READY))
                .thenReturn(true);

        assertThatThrownBy(() -> auctionRoomService.update(1L, 10L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_ALREADY_STARTED);

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

    private AuctionRoomListItemResponseDto newListItem(Long auctionRoomId) {
        return AuctionRoomListItemResponseDto.builder()
                .auctionRoomId(auctionRoomId)
                .name("승민의 경매방")
                .status(AuctionRoomStatus.BEFORE)
                .createdAt(LocalDateTime.of(2026, 8, 3, 12, 0))
                .itemCount(2L)
                .build();
    }

    private SellerProfile givenSellerProfileForList() {
        SellerProfile sellerProfile = newSellerProfile();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", 5L);
        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        return sellerProfile;
    }

    @Test
    @DisplayName("내 경매방 목록을 조회하면 요청한 쪽 크기만큼만 담기고 다음 커서가 채워진다")
    void getMyRooms_hasNext() {

        givenSellerProfileForList();

        // size 2를 요청하면 리포지토리는 hasNext 판정용으로 3건을 돌려준다.
        when(auctionRoomRepository.search(5L, null, null, null, 2))
                .thenReturn(List.of(newListItem(30L), newListItem(20L), newListItem(10L)));

        CursorPageResponse<AuctionRoomListItemResponseDto> response =
                auctionRoomService.getMyRooms(1L, null, null, null, 2);

        assertThat(response.content()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(20L);
    }

    @Test
    @DisplayName("마지막 쪽이면 다음 커서가 비어 있다")
    void getMyRooms_lastPage() {

        givenSellerProfileForList();

        when(auctionRoomRepository.search(5L, null, null, null, 2))
                .thenReturn(List.of(newListItem(30L)));

        CursorPageResponse<AuctionRoomListItemResponseDto> response =
                auctionRoomService.getMyRooms(1L, null, null, null, 2);

        assertThat(response.content()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("쪽 크기를 주지 않으면 기본값으로 조회한다")
    void getMyRooms_defaultPageSize() {

        givenSellerProfileForList();

        when(auctionRoomRepository.search(5L, null, null, null, AuctionRoomRepository.DEFAULT_PAGE_SIZE))
                .thenReturn(List.of());

        CursorPageResponse<AuctionRoomListItemResponseDto> response =
                auctionRoomService.getMyRooms(1L, null, null, null, null);

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 목록 조회 시 예외가 발생한다")
    void getMyRooms_sellerProfileNotFound() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionRoomService.getMyRooms(1L, null, null, null, null))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }
}
