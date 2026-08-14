package com.hot6ix.upbid.global.interceptor;

import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.session.SessionKeys;
import com.hot6ix.upbid.global.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionManager sessionManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        boolean guestAllowed = handlerMethod.hasMethodAnnotation(GuestAllowed.class);

        if (guestAllowed && !usesLoginUserId(handlerMethod)) {
            return true;
        }

        sessionManager.findUserId(request).ifPresentOrElse(
                userId -> request.setAttribute(SessionKeys.USER_ID, userId),
                () -> {
                    if (!guestAllowed) {
                        throw new ApplicationException(AuthErrorType.UNAUTHORIZED);
                    }
                }
        );

        return true;
    }

    /**
     * {@code @LoginUserId}는 컨트롤러가 아니라 {@code ~~~Api} 인터페이스 쪽 파라미터에 선언돼
     * 있어서, {@code handlerMethod.getMethod().getParameterAnnotations()}가 아니라
     * {@code MethodParameter.hasParameterAnnotation()}으로 확인해야 인터페이스까지 잡힌다.
     */
    private boolean usesLoginUserId(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(LoginUserId.class));
    }
}
