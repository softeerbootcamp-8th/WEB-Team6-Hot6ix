package com.hot6ix.upbid.global.interceptor;

import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.session.SessionKeys;
import com.hot6ix.upbid.global.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
}
