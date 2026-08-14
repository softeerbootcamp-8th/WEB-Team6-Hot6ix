package com.hot6ix.upbid.global.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class SessionManager {

    @Value("${spring.session.timeout}")
    private Duration loginTimeout;

    @Value("${server.servlet.session.cookie.name}")
    private String cookieName;

    public void create(HttpServletRequest request, Long userId) {

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setMaxInactiveInterval((int) loginTimeout.toSeconds());
        session.setAttribute(SessionKeys.USER_ID, userId);
    }

    public Optional<Long> findUserId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }

        // GenericJackson(2)JsonRedisSerializer는 Long 같은 "natural type"에는 타입 정보를
        // 안 붙인다(둘 다 확인함). Redis 왕복 후 Object로 역직렬화하면 Integer로 돌아올 수
        // 있어서, Number로 받아 longValue()로 좁혀야 ClassCastException을 피한다.
        return Optional.ofNullable((Number) session.getAttribute(SessionKeys.USER_ID))
                .map(Number::longValue);
    }

    public void invalidate(HttpServletRequest request, HttpServletResponse response) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        expireSessionCookie(response);
    }

    private void expireSessionCookie(HttpServletResponse response) {
        ResponseCookie expiredCookie = ResponseCookie.from(cookieName, "")
                .path("/")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
    }
}
