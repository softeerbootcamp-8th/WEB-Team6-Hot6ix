package com.hot6ix.upbid.domain.bid.interceptor;

import com.hot6ix.upbid.domain.bid.exception.BidErrorType;
import com.hot6ix.upbid.domain.bid.ratelimit.BidRateLimiter;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.session.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 입찰 API에 Token Bucket 리미터를 적용한다.
 *
 * <p>{@code AuthInterceptor}가 먼저 등록되어 로그인 사용자 ID를
 * {@link SessionKeys#USER_ID} 요청 속성에 채워두므로, 여기서는 그 값을 재사용한다.
 * 비로그인 요청은 {@code AuthInterceptor}가 이미 401로 막거나 {@code @GuestAllowed}로
 * 허용한 대상이라 리미터 대상이 아니다.
 */
@Component
@RequiredArgsConstructor
public class BidRateLimitInterceptor implements HandlerInterceptor {

    private final BidRateLimiter bidRateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        Object userId = request.getAttribute(SessionKeys.USER_ID);

        if (userId == null) {
            return true;
        }

        if (bidRateLimiter.isRateLimited((Long) userId)) {
            throw new ApplicationException(BidErrorType.TOO_MANY_BIDS);
        }

        return true;
    }
}
