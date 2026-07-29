package com.hot6ix.upbid.global.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SessionManager {

    public void create(HttpServletRequest request, Long userId) {

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(SessionKeys.USER_ID, userId);
    }

    public Optional<Long> findUserId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }

        return Optional.ofNullable((Long) session.getAttribute(SessionKeys.USER_ID));
    }

    public void invalidate(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }
    }
}
