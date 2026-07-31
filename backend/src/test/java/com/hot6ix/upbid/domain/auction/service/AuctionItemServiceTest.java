package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
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

@ExtendWith(MockitoExtension.class)
class AuctionItemServiceTest {

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private AuctionRoomRepository auctionRoomRepository;

    @InjectMocks
    private AuctionItemService auctionItemService;

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
                50_000L,
                1_000L,
                AuctionItemStatus.SOLD,
                LocalDateTime.of(2026, 7, 29, 21, 0));

        when(auctionItemRepository.findDetail(1L)).thenReturn(Optional.of(sold));

        AuctionItemDetailResponseDto result = auctionItemService.getDetail(1L);

        assertThat(result.status()).isEqualTo(AuctionItemStatus.SOLD);
    }
}
