package com.hot6ix.upbid.global.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class SessionManagerTest {

    private static final Long USER_ID = 1L;

    private final SessionManager sessionManager = new SessionManager();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("세션이 없으면 새로 만들고 userId를 담는다")
        void createsSessionAndStoresUserId() {

            sessionManager.create(request, USER_ID);

            assertThat(request.getSession(false)).isNotNull();
            assertThat(request.getSession(false).getAttribute(SessionKeys.USER_ID)).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("이미 세션이 있어도 로그인 시 세션 ID를 새로 발급한다")
        void rotatesSessionId() {

            String previousSessionId = request.getSession(true).getId();

            sessionManager.create(request, USER_ID);

            assertThat(request.getSession(false).getId()).isNotEqualTo(previousSessionId);
        }

        @Test
        @DisplayName("세션 ID가 바뀌어도 userId는 새 세션에서 조회된다")
        void keepsUserIdAfterSessionIdRotation() {

            request.getSession(true);

            sessionManager.create(request, USER_ID);

            assertThat(sessionManager.findUserId(request)).contains(USER_ID);
        }
    }

    @Nested
    @DisplayName("findUserId")
    class FindUserId {

        @Test
        @DisplayName("세션이 없으면 빈 Optional을 반환한다")
        void returnsEmptyWhenNoSession() {

            assertThat(sessionManager.findUserId(request)).isEmpty();
        }

        @Test
        @DisplayName("세션을 조회만 하고 새로 만들지 않는다")
        void doesNotCreateSession() {

            sessionManager.findUserId(request);

            assertThat(request.getSession(false)).isNull();
        }

        @Test
        @DisplayName("세션은 있지만 userId가 없으면 빈 Optional을 반환한다")
        void returnsEmptyWhenSessionHasNoUserId() {

            request.getSession(true);

            assertThat(sessionManager.findUserId(request)).isEmpty();
        }

        @Test
        @DisplayName("세션에 담긴 userId를 반환한다")
        void returnsStoredUserId() {

            request.getSession(true).setAttribute(SessionKeys.USER_ID, USER_ID);

            assertThat(sessionManager.findUserId(request)).isEqualTo(Optional.of(USER_ID));
        }
    }

    @Nested
    @DisplayName("invalidate")
    class Invalidate {

        @Test
        @DisplayName("세션을 무효화한다")
        void invalidatesSession() {

            MockHttpSession session = (MockHttpSession) request.getSession(true);
            session.setAttribute(SessionKeys.USER_ID, USER_ID);

            sessionManager.invalidate(request);

            assertThat(session.isInvalid()).isTrue();
        }

        @Test
        @DisplayName("무효화 이후에는 userId를 더 이상 조회할 수 없다")
        void userIdIsNotFoundAfterInvalidate() {

            request.getSession(true).setAttribute(SessionKeys.USER_ID, USER_ID);

            sessionManager.invalidate(request);

            assertThat(sessionManager.findUserId(request)).isEmpty();
        }

        @Test
        @DisplayName("세션이 없어도 예외 없이 통과한다")
        void doesNothingWhenNoSession() {

            assertThatCode(() -> sessionManager.invalidate(request)).doesNotThrowAnyException();
            assertThat(request.getSession(false)).isNull();
        }

        @Test
        @DisplayName("이미 무효화된 세션에 다시 호출해도 예외가 발생하지 않는다")
        void isIdempotent() {

            request.getSession(true).setAttribute(SessionKeys.USER_ID, USER_ID);

            sessionManager.invalidate(request);

            assertThatCode(() -> sessionManager.invalidate(request)).doesNotThrowAnyException();
        }
    }
}
