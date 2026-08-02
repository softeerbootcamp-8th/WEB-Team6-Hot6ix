package com.hot6ix.upbid.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.deal.dto.response.DealSummaryResponseDto;
import com.hot6ix.upbid.domain.deal.entity.DealItemStatus;
import com.hot6ix.upbid.domain.deal.entity.DealRole;
import com.hot6ix.upbid.domain.deal.repository.DealRepository;
import com.hot6ix.upbid.domain.deal.repository.DealSummaryProjection;
import java.time.LocalDateTime;
import java.util.List;
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
    private static final LocalDateTime CLOSED_AT = LocalDateTime.of(2026, 7, 29, 21, 0);

    @Mock
    private DealRepository dealRepository;

    @InjectMocks
    private DealService dealService;

    /** 프로젝션은 인터페이스라 익명 구현으로 만든다. 판정에 쓰이는 세 값만 달라진다. */
    private DealSummaryProjection deal(int sellerRow, AuctionItemStatus itemStatus, int dealCompleted) {
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
                deal(1, AuctionItemStatus.SOLD, 0),
                deal(0, AuctionItemStatus.SOLD, 0)))
                .extracting(DealSummaryResponseDto::role, DealSummaryResponseDto::productId)
                .containsExactly(
                        tuple(DealRole.SELLER, 3L),
                        tuple(DealRole.BUYER, null));
    }

    @Test
    @DisplayName("성사된 후보가 있으면 COMPLETED, 없으면 IN_PROGRESS다")
    void getDealsMapsDealStatus() {

        assertThat(getDeals(
                deal(1, AuctionItemStatus.SOLD, 1),
                deal(1, AuctionItemStatus.SOLD, 0)))
                .extracting(DealSummaryResponseDto::status)
                .containsExactly(DealItemStatus.COMPLETED, DealItemStatus.IN_PROGRESS);
    }

    /** 유찰은 입찰이 없어 거래가 시작된 적이 없다. 성사 여부보다 먼저 판정해야 한다. */
    @Test
    @DisplayName("유찰 물품은 UNSOLD다")
    void getDealsMapsUnsold() {

        assertThat(getDeals(deal(1, AuctionItemStatus.FAILED, 0)))
                .extracting(DealSummaryResponseDto::status)
                .containsExactly(DealItemStatus.UNSOLD);
    }

    @Test
    @DisplayName("거래가 없으면 빈 목록을 돌려주고 예외를 내지 않는다")
    void getDealsReturnsEmpty() {

        when(dealRepository.findDeals(USER_ID)).thenReturn(List.of());

        assertThat(dealService.getDeals(USER_ID)).isEmpty();
    }
}
