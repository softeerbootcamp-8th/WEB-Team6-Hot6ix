package com.hot6ix.upbid.global.logging;

import com.hot6ix.upbid.global.session.SessionKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 요청 하나를 한 줄로 남긴다.
 *
 * <p>지금까지 남던 로그는 "무슨 일이 일어났다"뿐이라, 요청이 성공했는지 몇 ms 걸렸는지가
 * 어디에도 없었다. 4xx 와 5xx 비율도 로그로는 볼 수 없었다.
 *
 * <p>{@link TraceIdFilter} 가 먼저 돌아 MDC 에 traceId 를 심으므로 이 줄과 그 요청이 남긴
 * 다른 줄들이 같은 값으로 묶인다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final String ACTUATOR_PREFIX = "/actuator";
    private static final String SSE_SUBSCRIBE_SUFFIX = "/subscribe";

    /**
     * 이 둘은 남기지 않는다.
     *
     * <p>{@code /actuator} 는 측정 서버의 Prometheus 가 5초마다 긁어서, 남기면 그것만으로
     * 하루 17,000줄이 쌓인다. 사람이 부르는 요청이 그 사이에 묻힌다.
     *
     * <p>SSE 구독은 연결이 끊길 때 비로소 이 필터가 끝나서, 걸린 시간이 "응답까지" 가 아니라
     * "연결이 살아 있던 시간" 으로 찍힌다. 한 시간 붙어 있었으면 3,600,000ms 짜리 줄이 남아
     * 응답 시간 통계를 통째로 망가뜨린다(같은 이유로 부하 측정 대시보드도 이 경로를 뺐다).
     * 접속과 종료는 {@code RoomSseManager} 가 이미 따로 남기고 있다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith(ACTUATOR_PREFIX) || uri.endsWith(SSE_SUBSCRIBE_SUFFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startedAt = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            write(request, response, elapsedMs);
        }
    }

    /**
     * 5xx 를 error 가 아니라 warn 으로 남긴다.
     *
     * <p>error 로 남기면 {@code SlackAppender} 가 집어가서, 이미 예외를 잡아 알림을 보낸
     * {@code GlobalExceptionHandler} 와 같은 실패로 두 번 알림이 간다.
     */
    private void write(HttpServletRequest request, HttpServletResponse response, long elapsedMs) {
        int status = response.getStatus();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        Object userId = request.getAttribute(SessionKeys.USER_ID);

        if (status >= 500) {
            log.warn("요청 - [{}] {} {} ({}ms) userId={}", method, uri, status, elapsedMs, userId);
        } else {
            log.info("요청 - [{}] {} {} ({}ms) userId={}", method, uri, status, elapsedMs, userId);
        }
    }
}
