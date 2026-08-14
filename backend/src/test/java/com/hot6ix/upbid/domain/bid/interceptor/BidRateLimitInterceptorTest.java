package com.hot6ix.upbid.domain.bid.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.bid.exception.BidErrorType;
import com.hot6ix.upbid.domain.bid.ratelimit.BidRateLimiter;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.session.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BidRateLimitInterceptorTest {

    private final BidRateLimiter bidRateLimiter = mock(BidRateLimiter.class);
    private final BidRateLimitInterceptor interceptor = new BidRateLimitInterceptor(bidRateLimiter);
    private final HttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("비로그인 요청은 리미터를 거치지 않고 통과한다")
    void passesThroughWhenNoLoginUser() {

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("리미터가 허용하면 통과한다")
    void passesThroughWhenNotRateLimited() {

        request.setAttribute(SessionKeys.USER_ID, 1L);
        when(bidRateLimiter.isRateLimited(1L)).thenReturn(false);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("리미터가 제한하면 예외를 던진다")
    void throwsWhenRateLimited() {

        request.setAttribute(SessionKeys.USER_ID, 1L);
        when(bidRateLimiter.isRateLimited(1L)).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(BidErrorType.TOO_MANY_BIDS.getMessage());
    }
}
