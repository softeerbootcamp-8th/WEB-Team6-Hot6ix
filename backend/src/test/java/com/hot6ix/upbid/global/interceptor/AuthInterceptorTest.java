package com.hot6ix.upbid.global.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.session.SessionKeys;
import com.hot6ix.upbid.global.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    private static final Long USER_ID = 1L;

    @Mock
    private SessionManager sessionManager;

    @InjectMocks
    private AuthInterceptor authInterceptor;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("handler가 HandlerMethod가 아니면 세션을 조회하지 않고 통과시킨다")
    void passesThroughNonHandlerMethod() {

        boolean result = authInterceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verifyNoInteractions(sessionManager);
    }

    @Test
    @DisplayName("로그인 상태면 보호된 API를 통과시키고 userId를 요청 속성에 담는다")
    void injectsUserIdWhenAuthenticated() throws NoSuchMethodException {

        로그인_상태();

        boolean result = authInterceptor.preHandle(request, response, handlerMethod("authRequired"));

        assertThat(result).isTrue();
        assertThat(request.getAttribute(SessionKeys.USER_ID)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("비로그인 상태로 보호된 API를 호출하면 UNAUTHORIZED 예외가 발생한다")
    void throwsUnauthorizedWhenNotAuthenticated() throws NoSuchMethodException {

        비로그인_상태();

        HandlerMethod handler = handlerMethod("authRequired");

        assertThatThrownBy(() -> authInterceptor.preHandle(request, response, handler))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuthErrorType.UNAUTHORIZED);

        assertThat(request.getAttribute(SessionKeys.USER_ID)).isNull();
    }

    @Test
    @DisplayName("@GuestAllowed이면서 @LoginUserId 파라미터가 없으면 세션 조회 자체를 하지 않고 통과시킨다")
    void skipsSessionLookupWhenGuestAllowedWithoutLoginUserIdParam() throws NoSuchMethodException {

        boolean result = authInterceptor.preHandle(request, response, handlerMethod("guestAllowed"));

        assertThat(result).isTrue();
        assertThat(request.getAttribute(SessionKeys.USER_ID)).isNull();
        verifyNoInteractions(sessionManager);
    }

    @Test
    @DisplayName("@GuestAllowed이면서 @LoginUserId 파라미터가 있으면 세션을 조회해서 로그인 상태면 userId를 담는다")
    void injectsUserIdOnGuestAllowedEndpointWithLoginUserIdParamWhenAuthenticated() throws NoSuchMethodException {

        로그인_상태();

        boolean result = authInterceptor.preHandle(request, response, handlerMethod("guestAllowedWithLoginUserId", Long.class));

        assertThat(result).isTrue();
        assertThat(request.getAttribute(SessionKeys.USER_ID)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("@GuestAllowed이면서 @LoginUserId 파라미터가 있으면 비로그인 상태여도 세션을 조회한 뒤 통과시킨다")
    void looksUpSessionOnGuestAllowedEndpointWithLoginUserIdParamWhenNotAuthenticated() throws NoSuchMethodException {

        비로그인_상태();

        boolean result = authInterceptor.preHandle(request, response, handlerMethod("guestAllowedWithLoginUserId", Long.class));

        assertThat(result).isTrue();
        assertThat(request.getAttribute(SessionKeys.USER_ID)).isNull();
        verify(sessionManager).findUserId(request);
    }

    private void 로그인_상태() {
        when(sessionManager.findUserId(any(HttpServletRequest.class))).thenReturn(Optional.of(USER_ID));
    }

    private void 비로그인_상태() {
        when(sessionManager.findUserId(any(HttpServletRequest.class))).thenReturn(Optional.empty());
    }

    private HandlerMethod handlerMethod(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        TestController controller = new TestController();
        return new HandlerMethod(controller, TestController.class.getMethod(methodName, parameterTypes));
    }

    /**
     * {@code @GuestAllowed}·{@code @LoginUserId} 유무가 다른 핸들러들을 만들기 위한 더미
     * 컨트롤러다.
     */
    static class TestController {

        public void authRequired() {
        }

        @GuestAllowed
        public void guestAllowed() {
        }

        @GuestAllowed
        public void guestAllowedWithLoginUserId(@LoginUserId Long userId) {
        }
    }
}
