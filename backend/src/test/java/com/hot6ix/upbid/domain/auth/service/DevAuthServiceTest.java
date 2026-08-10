package com.hot6ix.upbid.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * key 로 회원을 가르는 부분만 본다. 부하 측정에서 가상 사용자 N명을 서로 다른 회원으로
 * 만들려고 연 값이라, 여기가 어긋나면 전원이 같은 회원이 되어 두 번째 입찰부터
 * {@code ALREADY_TOP_BIDDER} 로 거절되고 측정 자체가 안 된다.
 */
@ExtendWith(MockitoExtension.class)
class DevAuthServiceTest {

    /** {@code users.provider_id} 컬럼 길이. 접두사까지 합쳐 이 안에 들어가야 한다. */
    private static final int PROVIDER_ID_COLUMN_LENGTH = 100;

    /** {@code users.nickname} 컬럼 길이. */
    private static final int NICKNAME_COLUMN_LENGTH = 20;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DevAuthService devAuthService;

    @Test
    @DisplayName("key 를 생략하면 예전과 같은 기본 회원을 쓴다")
    void usesDefaultUserWhenKeyIsAbsent() {

        givenNoExistingUser();

        devAuthService.findOrCreateDevUser(null);

        assertThat(savedUser().getProviderId()).isEqualTo("dev-test-user");
    }

    @Test
    @DisplayName("key 가 공백뿐이어도 기본 회원을 쓴다")
    void usesDefaultUserWhenKeyIsBlank() {

        givenNoExistingUser();

        devAuthService.findOrCreateDevUser("   ");

        assertThat(savedUser().getProviderId()).isEqualTo("dev-test-user");
    }

    @Test
    @DisplayName("key 가 다르면 다른 회원이 된다")
    void differentKeyMakesDifferentUser() {

        givenNoExistingUser();

        devAuthService.findOrCreateDevUser("bidder-1");

        assertThat(savedUser().getProviderId()).isEqualTo("dev-bidder-1");
    }

    @Test
    @DisplayName("같은 key 로 다시 부르면 있는 회원을 그대로 쓴다")
    void sameKeyReusesExistingUser() {

        User existing = userWithId(42L);
        when(userRepository.findByProviderAndProviderId(OauthProvider.DEV, "dev-bidder-1"))
                .thenReturn(Optional.of(existing));

        Long userId = devAuthService.findOrCreateDevUser("bidder-1");

        assertThat(userId).isEqualTo(42L);
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    /**
     * 컬럼 길이가 넘치면 저장 시점에 터진다. 접두사 {@code dev-} 4자를 빼면 96자가 상한이라
     * 경계가 딱 맞아떨어지므로, 접두사를 건드리면 이 테스트가 먼저 깨져야 한다.
     */
    @Test
    @DisplayName("key 가 아무리 길어도 provider_id 와 nickname 이 컬럼 길이를 안 넘는다")
    void longKeyIsTruncatedToFitColumns() {

        givenNoExistingUser();

        devAuthService.findOrCreateDevUser("x".repeat(500));

        User saved = savedUser();
        assertThat(saved.getProviderId()).hasSizeLessThanOrEqualTo(PROVIDER_ID_COLUMN_LENGTH);
        assertThat(saved.getNickname()).hasSizeLessThanOrEqualTo(NICKNAME_COLUMN_LENGTH);
    }

    // ── 도우미 ──────────────────────────────────────────────────────

    private void givenNoExistingUser() {
        when(userRepository.findByProviderAndProviderId(eq(OauthProvider.DEV), any()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    /** {@code userId} 는 DB 가 채우는 값이라 테스트에서는 심어 준다. */
    private User userWithId(Long userId) {
        User user = User.builder()
                .provider(OauthProvider.DEV)
                .providerId("dev-bidder-1")
                .nickname("bidder-1")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}
