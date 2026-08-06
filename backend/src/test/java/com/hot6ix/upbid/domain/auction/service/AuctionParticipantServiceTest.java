package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuctionParticipantServiceTest {

    @Mock
    private AuctionParticipantRepository auctionParticipantRepository;

    @Mock
    private AuctionRoomRepository auctionRoomRepository;

    @InjectMocks
    private AuctionParticipantService auctionParticipantService;

    @Test
    @DisplayName("shareCode로 방을 찾아 약관 동의를 기록한다")
    void agree_recordsAgreement() {

        when(auctionRoomRepository.findIdByShareCode("SHARE01")).thenReturn(Optional.of(7L));

        auctionParticipantService.agree("SHARE01", 3L);

        verify(auctionParticipantRepository)
                .recordAgreement(7L, 3L, AuctionParticipantService.CURRENT_TERMS_VERSION);
    }

    @Test
    @DisplayName("방을 찾지 못하면 예외를 던진다")
    void agree_throwsWhenRoomNotFound() {

        when(auctionRoomRepository.findIdByShareCode("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionParticipantService.agree("INVALID", 3L))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).getErrorType())
                        .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));
    }
}
