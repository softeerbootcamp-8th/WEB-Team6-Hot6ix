package com.hot6ix.upbid.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.deal.dto.response.AuctionItemDealStatusResponseDto;
import com.hot6ix.upbid.domain.deal.dto.response.AuctionRoomDealStatusResponseDto;
import com.hot6ix.upbid.domain.deal.dto.response.DealSummaryResponseDto;
import com.hot6ix.upbid.domain.deal.entity.DealItemStatus;
import com.hot6ix.upbid.domain.deal.entity.DealRole;
import com.hot6ix.upbid.domain.deal.repository.DealRepository;
import com.hot6ix.upbid.domain.deal.repository.DealSummaryProjection;
import com.hot6ix.upbid.domain.deal.repository.RoomDealStatusProjection;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 쿼리는 사실만 내보내고 역할·거래 상태 판정은 여기서 한다. 그 판정이 이 테스트의 대상이다.
 * UNION 쿼리 자체는 {@code DealRepositoryTest}가 실제 DB로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DealServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ROOM_ID = 1L;
    private static final LocalDateTime CLOSED_AT = LocalDateTime.of(2026, 7, 29, 21, 0);

    @Mock
    private DealRepository dealRepository;

    @Mock
    private AuctionRoomRepository auctionRoomRepository;

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    @InjectMocks
    private DealService dealService;

    /** 프로젝션은 인터페이스라 익명 구현으로 만든다. 판정에 쓰이는 네 값만 달라진다. */
    private DealSummaryProjection deal(
            int sellerRow, AuctionItemStatus itemStatus, int dealCompleted, int hasWaitingCandidate) {

        return new DealSummaryProjection() {
            public Integer getSellerRow() {
                return sellerRow;
            }

            public Long getAuctionItemId() {
                return 2L;
            }

            public Long getAuctionRoomId() {
                return 1L;
            }

            public Long getProductId() {
                return sellerRow == 1 ? 3L : null;
            }

            public String getProductName() {
                return "포토카드";
            }

            public String getAuctionRoomName() {
                return "승민상점 경매방";
            }

            public String getItemStatus() {
                return itemStatus.name();
            }

            public Integer getDealCompleted() {
                return dealCompleted;
            }

            public Integer getHasWaitingCandidate() {
                return hasWaitingCandidate;
            }

            public Long getAmount() {
                return 15_000L;
            }

            public String getPartnerNickname() {
                return "원기";
            }

            public Long getSellerProfileId() {
                return 4L;
            }

            public LocalDateTime getClosedAt() {
                return CLOSED_AT;
            }
        };
    }

    private List<DealSummaryResponseDto> getDeals(DealSummaryProjection... deals) {
        when(dealRepository.findDeals(USER_ID)).thenReturn(List.of(deals));
        return dealService.getDeals(USER_ID);
    }

    @Test
    @DisplayName("판매 쪽 행은 SELLER, 구매 쪽 행은 BUYER가 된다")
    void getDealsMapsRole() {

        assertThat(getDeals(
                deal(1, AuctionItemStatus.SOLD, 0, 1),
                deal(0, AuctionItemStatus.SOLD, 0, 1)))
                .extracting(DealSummaryResponseDto::role, DealSummaryResponseDto::productId)
                .containsExactly(
                        tuple(DealRole.SELLER, 3L),
                        tuple(DealRole.BUYER, null));
    }

    @Test
    @DisplayName("성사된 후보가 있으면 COMPLETED, 대기 후보가 있으면 IN_PROGRESS다")
    void getDealsMapsDealStatus() {

        assertThat(getDeals(
                deal(1, AuctionItemStatus.SOLD, 1, 0),
                deal(1, AuctionItemStatus.SOLD, 0, 1)))
                .extracting(DealSummaryResponseDto::status)
                .containsExactly(DealItemStatus.COMPLETED, DealItemStatus.IN_PROGRESS);
    }

    /**
     * 거래가 끝난 뒤에도 하위 순위는 WAITING으로 남는다. 대기 후보 여부보다 성사 여부를
     * 먼저 봐야 하는 이유다.
     */
    @Test
    @DisplayName("성사된 후보가 있으면 대기 후보가 남아 있어도 COMPLETED다")
    void getDealsPrefersCompletedOverWaiting() {

        assertThat(getDeals(deal(1, AuctionItemStatus.SOLD, 1, 1)))
                .extracting(DealSummaryResponseDto::status)
                .containsExactly(DealItemStatus.COMPLETED);
    }

    /** 유찰은 입찰이 없어 거래가 시작된 적이 없다. 성사 여부보다 먼저 판정해야 한다. */
    @Test
    @DisplayName("유찰 물품은 UNSOLD다")
    void getDealsMapsUnsold() {

        assertThat(getDeals(deal(1, AuctionItemStatus.FAILED, 0, 0)))
                .extracting(DealSummaryResponseDto::status)
                .containsExactly(DealItemStatus.UNSOLD);
    }

    /** 후보가 있었는데 전원 실패한 물품. 거래 중으로 보이면 판매자가 오지 않을 상대를 기다린다. */
    @Test
    @DisplayName("성사도 대기도 없으면 후보가 전원 실패한 것으로 보고 ALL_FAILED다")
    void getDealsMapsAllFailed() {

        assertThat(getDeals(deal(1, AuctionItemStatus.SOLD, 0, 0)))
                .extracting(DealSummaryResponseDto::status)
                .containsExactly(DealItemStatus.ALL_FAILED);
    }

    @Test
    @DisplayName("거래가 없으면 빈 목록을 돌려주고 예외를 내지 않는다")
    void getDealsReturnsEmpty() {

        when(dealRepository.findDeals(USER_ID)).thenReturn(List.of());

        assertThat(dealService.getDeals(USER_ID)).isEmpty();
    }

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
                .build();
    }

    private AuctionRoom newAuctionRoom(SellerProfile sellerProfile) {
        return AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name("승민상점 경매방")
                .status(AuctionRoomStatus.CLOSED)
                .build();
    }

    /** 소유자 검증을 통과시킨다. 검증 자체는 아래 실패 케이스가 따로 본다. */
    private void 소유자로_인정한다(SellerProfile sellerProfile) {
        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, sellerProfile.getSellerProfileId()))
                .thenReturn(Optional.of(newAuctionRoom(sellerProfile)));
    }

    private RoomDealStatusProjection roomItem(
            AuctionItemStatus itemStatus, int dealCompleted, int hasWaitingCandidate,
            Long dealCandidateId, Long amount, String partnerNickname,
            int candidateCount, int failedCandidateCount) {

        return new RoomDealStatusProjection() {
            public Long getAuctionItemId() {
                return 2L;
            }

            public String getProductName() {
                return "포토카드";
            }

            public String getItemStatus() {
                return itemStatus.name();
            }

            public Integer getDealCompleted() {
                return dealCompleted;
            }

            public Integer getHasWaitingCandidate() {
                return hasWaitingCandidate;
            }

            public Long getDealCandidateId() {
                return dealCandidateId;
            }

            public Long getAmount() {
                return amount;
            }

            public String getPartnerNickname() {
                return partnerNickname;
            }

            public Integer getCandidateCount() {
                return candidateCount;
            }

            public Integer getFailedCandidateCount() {
                return failedCandidateCount;
            }
        };
    }

    @Test
    @DisplayName("거래 현황은 지금 상대와 그 상대의 금액을 함께 담는다")
    void getRoomDeals() {

        SellerProfile sellerProfile = newSellerProfile();
        소유자로_인정한다(sellerProfile);
        when(dealRepository.findRoomDealStatuses(ROOM_ID)).thenReturn(List.of(
                // 1순위가 실패해 2순위로 넘어간 상태. 금액도 2순위가 부른 값이어야 한다.
                roomItem(AuctionItemStatus.SOLD, 0, 1, 55L, 12_000L, "원기", 3, 1)));

        AuctionRoomDealStatusResponseDto response = dealService.getRoomDeals(ROOM_ID, USER_ID);

        assertThat(response.auctionRoomId()).isEqualTo(newAuctionRoom(sellerProfile).getAuctionRoomId());
        assertThat(response.name()).isEqualTo("승민상점 경매방");

        AuctionItemDealStatusResponseDto item = response.items().getFirst();
        assertThat(item.dealStatus()).isEqualTo(DealItemStatus.IN_PROGRESS);
        assertThat(item.dealCandidateId()).isEqualTo(55L);
        assertThat(item.amount()).isEqualTo(12_000L);
        assertThat(item.partnerNickname()).isEqualTo("원기");
        assertThat(item.candidateCount()).isEqualTo(3);
        assertThat(item.failedCandidateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("거래 현황도 거래 내역과 같은 상태 판정을 쓴다")
    void getRoomDealsSharesStatusRule() {

        소유자로_인정한다(newSellerProfile());
        when(dealRepository.findRoomDealStatuses(ROOM_ID)).thenReturn(List.of(
                roomItem(AuctionItemStatus.SOLD, 1, 0, 55L, 12_000L, "원기", 2, 0),
                roomItem(AuctionItemStatus.FAILED, 0, 0, null, null, null, 0, 0),
                // 후보 3명이 전부 실패해 상대가 없다.
                roomItem(AuctionItemStatus.SOLD, 0, 0, null, null, null, 3, 3)));

        assertThat(dealService.getRoomDeals(ROOM_ID, USER_ID).items())
                .extracting(AuctionItemDealStatusResponseDto::dealStatus)
                .containsExactly(
                        DealItemStatus.COMPLETED,
                        DealItemStatus.UNSOLD,
                        DealItemStatus.ALL_FAILED);
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 거래 현황 조회 시 예외가 발생한다")
    void getRoomDeals_sellerProfileNotFound() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> dealService.getRoomDeals(ROOM_ID, USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }

    /** 없는 방과 남의 방을 구분하지 않는다. 나누면 방의 존재 여부가 새어 나간다. */
    @Test
    @DisplayName("경매방이 없거나 본인 소유가 아니면 거래 현황 조회 시 404다")
    void getRoomDeals_roomNotFoundOrNotOwned() {

        SellerProfile sellerProfile = newSellerProfile();
        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, sellerProfile.getSellerProfileId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> dealService.getRoomDeals(ROOM_ID, USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }
}
