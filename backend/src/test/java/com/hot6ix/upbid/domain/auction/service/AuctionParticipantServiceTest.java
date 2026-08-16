package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionParticipantServiceTest {

    @Mock
    private AuctionParticipantRepository auctionParticipantRepository;

    @Mock
    private AuctionRoomRepository auctionRoomRepository;

    /** 동의한 회원을 Redis 참여자 SET에도 넣는다(이슈 #246의 비교군 C). */
    @Mock
    private AuctionRedisStore auctionRedisStore;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuctionParticipantService auctionParticipantService;

    @Test
    @DisplayName("shareCode로 방을 찾아 약관 동의를 기록한다")
    void agree_recordsAgreement() {

        when(auctionRoomRepository.findIdByShareCode("SHARE01")).thenReturn(Optional.of(7L));
        User user = User.builder().nickname("한기").build();
        ReflectionTestUtils.setField(user, "userId", 3L);
        when(userRepository.findByUserIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(user));

        auctionParticipantService.agree("SHARE01", 3L);

        verify(auctionParticipantRepository)
                .recordAgreement(7L, 3L, AuctionParticipantService.CURRENT_TERMS_VERSION);
        verify(auctionRedisStore).addParticipant(7L, 3L, "한기");
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
