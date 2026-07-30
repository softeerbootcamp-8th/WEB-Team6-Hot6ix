package com.hot6ix.upbid.domain.deal.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.hot6ix.upbid.domain.auction.exception.AuctionItemErrorType;
import com.hot6ix.upbid.domain.deal.service.DealCandidateService;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DealCandidateAwardListenerTest {

    @Mock
    private DealCandidateService dealCandidateService;

    @InjectMocks
    private DealCandidateAwardListener dealCandidateAwardListener;

    private final ItemEnded event =
            ItemEnded.of(1L, 2L, "포토카드", 15_000L, "원기", LocalDateTime.of(2026, 7, 29, 21, 0));

    @Test
    @DisplayName("마감 이벤트를 받으면 낙찰 후보 생성을 위임한다")
    void delegatesToService() {

        dealCandidateAwardListener.on(event);

        verify(dealCandidateService).award(event);
    }

    @Test
    @DisplayName("후보 생성이 실패해도 예외를 발행 측으로 전파하지 않는다")
    void swallowsFailure() {

        doThrow(new ApplicationException(AuctionItemErrorType.AUCTION_ITEM_NOT_FOUND))
                .when(dealCandidateService).award(event);

        assertThatCode(() -> dealCandidateAwardListener.on(event)).doesNotThrowAnyException();
    }
}
