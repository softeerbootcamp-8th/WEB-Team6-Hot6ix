package com.hot6ix.upbid.domain.auction.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
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

    @InjectMocks
    private AuctionParticipantService auctionParticipantService;

    @Test
    @DisplayName("로그인 사용자면 참여 행을 넣는다")
    void record_insertsForMember() {

        auctionParticipantService.record(7L, 3L);

        verify(auctionParticipantRepository).insertIfAbsent(7L, 3L);
    }

    @Test
    @DisplayName("게스트면 저장을 시도하지 않는다")
    void record_skipsGuest() {

        auctionParticipantService.record(7L, null);

        verifyNoInteractions(auctionParticipantRepository);
    }
}
