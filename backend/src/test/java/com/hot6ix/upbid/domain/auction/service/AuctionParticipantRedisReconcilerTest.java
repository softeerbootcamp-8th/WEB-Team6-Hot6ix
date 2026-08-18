package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuctionParticipantRedisReconcilerTest {

    @Mock
    private AuctionParticipantRepository auctionParticipantRepository;

    @Mock
    private AuctionRedisStore auctionRedisStore;

    @InjectMocks
    private AuctionParticipantRedisReconciler reconciler;

    @Test
    @DisplayName("DB에 동의한 사용자를 Redis 참여자로 복구한다")
    void restoresAgreedParticipant() {

        var participant = mock(AuctionParticipantRepository.AgreedParticipantForItem.class);
        when(participant.getRoomId()).thenReturn(7L);
        when(participant.getNickname()).thenReturn("한기");
        when(auctionParticipantRepository.findAgreedParticipantForItem(2L, 3L))
                .thenReturn(Optional.of(participant));

        boolean restored = reconciler.restoreIfAgreed(2L, 3L);

        assertThat(restored).isTrue();
        verify(auctionRedisStore).addParticipant(7L, 3L, "한기");
    }

    @Test
    @DisplayName("DB에 동의가 없으면 Redis를 변경하지 않는다")
    void skipsParticipantWithoutAgreement() {

        when(auctionParticipantRepository.findAgreedParticipantForItem(2L, 3L))
                .thenReturn(Optional.empty());

        boolean restored = reconciler.restoreIfAgreed(2L, 3L);

        assertThat(restored).isFalse();
        verify(auctionRedisStore, never()).addParticipant(7L, 3L, "한기");
    }
}
