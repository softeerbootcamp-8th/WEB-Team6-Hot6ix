package com.hot6ix.upbid.domain.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.auth.domain.PendingSignup;
import com.hot6ix.upbid.global.session.SessionKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PendingSignupManagerTest {

    private static final PendingSignup PENDING_SIGNUP = new PendingSignup(
            OauthProvider.KAKAO, "1234567890", "user@hot6ix.com", "승민", null);

    private final PendingSignupManager pendingSignupManager = new PendingSignupManager();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("세션이 없으면 새로 만들어 저장한다")
        void createsSessionAndStores() {

            pendingSignupManager.save(request, PENDING_SIGNUP);

            assertThat(request.getSession(false)).isNotNull();
            assertThat(request.getSession(false).getAttribute(SessionKeys.PENDING_SIGNUP))
                    .isEqualTo(PENDING_SIGNUP);
        }

        @Test
        @DisplayName("다시 저장하면 이전 값을 덮어쓴다")
        void overwritesPreviousValue() {

            pendingSignupManager.save(request, PENDING_SIGNUP);
            PendingSignup verified = PENDING_SIGNUP.withVerified("010-9999-8888");

            pendingSignupManager.save(request, verified);

            assertThat(pendingSignupManager.find(request)).contains(verified);
        }
    }

    @Nested
    @DisplayName("find")
    class Find {

        @Test
        @DisplayName("저장된 가입 대기 정보를 반환한다")
        void returnsStoredValue() {

            pendingSignupManager.save(request, PENDING_SIGNUP);

            assertThat(pendingSignupManager.find(request)).contains(PENDING_SIGNUP);
        }

        @Test
        @DisplayName("세션이 아예 없으면 빈 Optional을 반환한다")
        void returnsEmptyWhenNoSession() {

            assertThat(pendingSignupManager.find(request)).isEmpty();
        }

        @Test
        @DisplayName("세션은 있지만 가입 대기 정보가 없으면 빈 Optional을 반환한다")
        void returnsEmptyWhenAttributeAbsent() {

            request.getSession(true);

            assertThat(pendingSignupManager.find(request)).isEmpty();
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("저장된 가입 대기 정보를 제거한다")
        void removesStoredValue() {

            pendingSignupManager.save(request, PENDING_SIGNUP);

            pendingSignupManager.clear(request);

            assertThat(pendingSignupManager.find(request)).isEmpty();
        }

        @Test
        @DisplayName("세션이 없어도 예외 없이 동작한다")
        void doesNotFailWhenNoSession() {

            pendingSignupManager.clear(request);

            assertThat(pendingSignupManager.find(request)).isEmpty();
        }
    }
}
