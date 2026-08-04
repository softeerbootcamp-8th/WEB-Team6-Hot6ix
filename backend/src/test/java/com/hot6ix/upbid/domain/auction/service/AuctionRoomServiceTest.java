package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResultResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.MyAuctionRoomResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemResultProjection;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.auction.repository.RoomItemCountProjection;
import com.hot6ix.upbid.domain.deal.entity.DealRole;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.domain.deal.repository.MyCandidateRankProjection;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
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

    @Mock
    private DealCandidateRepository dealCandidateRepository;

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

    private AuctionRoom newClosedRoom() {
        return AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(newSellerProfile())
                .name("승민의 경매방")
                .status(AuctionRoomStatus.CLOSED)
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();
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

        when(auctionRoomRepository.findByAuctionRoomIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(newClosedRoom()));
        when(auctionItemRepository.findResults(10L)).thenReturn(newResultRows());
        when(dealCandidateRepository.findMyRanksInRoom(10L, 1L)).thenReturn(List.of());

        AuctionRoomResultResponseDto response = auctionRoomService.getResults(10L, 1L);

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

        when(auctionRoomRepository.findByAuctionRoomIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(newClosedRoom()));
        when(auctionItemRepository.findResults(10L)).thenReturn(List.of(
                new AuctionItemResultProjection(
                        103L, "진행 중인 물품", null,
                        AuctionItemStatus.IN_PROGRESS, 50_000L, "지금1위")));
        when(dealCandidateRepository.findMyRanksInRoom(10L, 1L)).thenReturn(List.of());

        AuctionRoomResultResponseDto response = auctionRoomService.getResults(10L, 1L);

        assertThat(response.items().getFirst().status()).isEqualTo(AuctionItemStatus.IN_PROGRESS);
        assertThat(response.items().getFirst().winnerNickname()).isNull();
        assertThat(response.items().getFirst().finalPrice()).isNull();
    }

    @Test
    @DisplayName("후보로 오른 물품에만 내 순위가 담긴다")
    void getResults_fillsMyRankOnlyWhereCandidate() {

        when(auctionRoomRepository.findByAuctionRoomIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(newClosedRoom()));
        when(auctionItemRepository.findResults(10L)).thenReturn(newResultRows());
        when(dealCandidateRepository.findMyRanksInRoom(10L, 1L))
                .thenReturn(List.of(new MyCandidateRankProjection(101L, 7, 60_000L)));

        AuctionRoomResultResponseDto response = auctionRoomService.getResults(10L, 1L);

        assertThat(response.items().getFirst().myRank()).isEqualTo(7);
        assertThat(response.items().getFirst().myAmount()).isEqualTo(60_000L);
        assertThat(response.items().getLast().myRank()).isNull();
        assertThat(response.items().getLast().myAmount()).isNull();
    }

    @Test
    @DisplayName("비로그인 요청은 내 순위를 조회하지 않고 전부 비운다")
    void getResults_guestHasNoMyRank() {

        when(auctionRoomRepository.findByAuctionRoomIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(newClosedRoom()));
        when(auctionItemRepository.findResults(10L)).thenReturn(newResultRows());

        AuctionRoomResultResponseDto response = auctionRoomService.getResults(10L, null);

        assertThat(response.items())
                .allSatisfy(item -> {
                    assertThat(item.myRank()).isNull();
                    assertThat(item.myAmount()).isNull();
                });
        verify(dealCandidateRepository, never()).findMyRanksInRoom(any(), any());
    }

    /** 방 하나. 정렬 키가 생성 시각과 ID라 둘 다 넣어 준다 — 조회로 올라온 방에는 값이 있다. */
    private AuctionRoom newRoom(long id, LocalDateTime createdAt, AuctionRoomStatus status) {
        AuctionRoom room = AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(newSellerProfile())
                .name("승민의 경매방")
                .status(status)
                .build();
        ReflectionTestUtils.setField(room, "auctionRoomId", id);
        ReflectionTestUtils.setField(room, "createdAt", createdAt);
        return room;
    }

    @Test
    @DisplayName("개설방은 SELLER, 참여방은 BUYER 역할로 한 목록에 합쳐진다")
    void getMyRoomsMergesBothSidesWithRole() {

        AuctionRoom owned = newRoom(10L, LocalDateTime.of(2026, 7, 30, 21, 0), AuctionRoomStatus.OPEN);
        AuctionRoom joined = newRoom(11L, LocalDateTime.of(2026, 7, 29, 21, 0), AuctionRoomStatus.CLOSED);

        when(auctionRoomRepository.findOwnedRooms(1L)).thenReturn(List.of(owned));
        when(auctionRoomRepository.findParticipatedRooms(1L)).thenReturn(List.of(joined));
        when(auctionItemRepository.countByAuctionRoomIds(List.of(10L, 11L)))
                .thenReturn(List.of(new RoomItemCountProjection(10L, 3L)));

        assertThat(auctionRoomService.getMyRooms(1L))
                .extracting(MyAuctionRoomResponseDto::auctionRoomId,
                        MyAuctionRoomResponseDto::role,
                        MyAuctionRoomResponseDto::status,
                        MyAuctionRoomResponseDto::itemCount)
                .containsExactly(
                        tuple(10L, DealRole.SELLER, AuctionRoomStatus.OPEN, 3L),
                        // 물품이 없는 방은 집계에 행이 없어 0으로 채워진다.
                        tuple(11L, DealRole.BUYER, AuctionRoomStatus.CLOSED, 0L));
    }

    /** 두 쿼리 결과를 합친 뒤 정렬하므로, 섞였을 때 순서가 유지되는지가 회귀 지점이다. */
    @Test
    @DisplayName("목록은 최근에 만든 방이 먼저 온다")
    void getMyRoomsSortsRecentFirst() {

        AuctionRoom old = newRoom(10L, LocalDateTime.of(2026, 7, 20, 21, 0), AuctionRoomStatus.CLOSED);
        AuctionRoom recent = newRoom(11L, LocalDateTime.of(2026, 7, 30, 21, 0), AuctionRoomStatus.OPEN);

        when(auctionRoomRepository.findOwnedRooms(1L)).thenReturn(List.of(old));
        when(auctionRoomRepository.findParticipatedRooms(1L)).thenReturn(List.of(recent));
        when(auctionItemRepository.countByAuctionRoomIds(List.of(11L, 10L))).thenReturn(List.of());

        assertThat(auctionRoomService.getMyRooms(1L))
                .extracting(MyAuctionRoomResponseDto::auctionRoomId)
                .containsExactly(11L, 10L);
    }

    /** 생성 시각이 같으면 ID 큰 쪽이 먼저다. 키가 하나면 순서가 요청마다 흔들린다. */
    @Test
    @DisplayName("생성 시각이 같으면 ID가 큰 방이 먼저 온다")
    void getMyRoomsBreaksTieById() {

        LocalDateTime sameMoment = LocalDateTime.of(2026, 7, 30, 21, 0);

        when(auctionRoomRepository.findOwnedRooms(1L)).thenReturn(List.of(
                newRoom(10L, sameMoment, AuctionRoomStatus.OPEN),
                newRoom(12L, sameMoment, AuctionRoomStatus.OPEN),
                newRoom(11L, sameMoment, AuctionRoomStatus.OPEN)));
        when(auctionRoomRepository.findParticipatedRooms(1L)).thenReturn(List.of());
        when(auctionItemRepository.countByAuctionRoomIds(List.of(12L, 11L, 10L))).thenReturn(List.of());

        assertThat(auctionRoomService.getMyRooms(1L))
                .extracting(MyAuctionRoomResponseDto::auctionRoomId)
                .containsExactly(12L, 11L, 10L);
    }

    @Test
    @DisplayName("경매방이 없으면 빈 목록을 돌려주고 물품 수를 세지 않는다")
    void getMyRoomsReturnsEmpty() {

        when(auctionRoomRepository.findOwnedRooms(1L)).thenReturn(List.of());
        when(auctionRoomRepository.findParticipatedRooms(1L)).thenReturn(List.of());

        assertThat(auctionRoomService.getMyRooms(1L)).isEmpty();
        // 빈 목록으로 in () 을 만들면 문법 오류다.
        verify(auctionItemRepository, never()).countByAuctionRoomIds(any());
    }

    @Test
    @DisplayName("존재하지 않거나 삭제된 경매방의 결과를 조회하면 예외가 발생한다")
    void getResults_notFound() {

        when(auctionRoomRepository.findByAuctionRoomIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionRoomService.getResults(999L, 1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }
}
