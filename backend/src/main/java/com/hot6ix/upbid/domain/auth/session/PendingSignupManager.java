package com.hot6ix.upbid.domain.auth.session;

import com.hot6ix.upbid.domain.auth.domain.PendingSignup;
import com.hot6ix.upbid.global.session.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PendingSignupManager {

    public void save(HttpServletRequest request, PendingSignup pendingSignup) {

        HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.PENDING_SIGNUP, pendingSignup);
    }

    public Optional<PendingSignup> find(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }

        PendingSignup pendingSignup = (PendingSignup) session.getAttribute(SessionKeys.PENDING_SIGNUP);

        return Optional.ofNullable(pendingSignup);
    }

    public void clear(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SessionKeys.PENDING_SIGNUP);
        }
    }
}
