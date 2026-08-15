package com.hot6ix.upbid.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hot6ix.upbid.global.session.SessionKeys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

/**
 * 무엇을 남기고 무엇을 남기지 않는지를 검증한다.
 *
 * <p>제외 목록이 이 클래스의 알맹이다. 잘못 풀면 Prometheus 스크랩이 하루 17,000줄을 쌓아
 * 사람이 부른 요청을 덮고, SSE 구독 한 건이 수백만 ms 짜리 줄로 남아 응답 시간 통계를 망친다.
 */
class AccessLogFilterTest {

    private AccessLogFilter accessLogFilter;
    private Logger logger;
    private ListAppender<ILoggingEvent> captured;

    @BeforeEach
    void setUp() {
        accessLogFilter = new AccessLogFilter();

        captured = new ListAppender<>();
        captured.start();

        logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
        logger.addAppender(captured);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(captured);
    }

    @Test
    @DisplayName("액추에이터 스크랩은 남기지 않는다")
    void skipsActuator() throws Exception {
        doFilter(request("GET", "/actuator/prometheus"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(captured.list).isEmpty();
    }

    @Test
    @DisplayName("SSE 구독은 남기지 않는다")
    void skipsSseSubscribe() throws Exception {
        MockHttpServletRequest request =
                request("GET", "/api/v1/auction-rooms/share/A1B2C3/subscribe");

        doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(captured.list).isEmpty();
    }

    @Test
    @DisplayName("일반 요청은 경로와 상태, 걸린 시간, userId 를 남긴다")
    void writesOneLinePerRequest() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/auction-items/42/bids");
        request.setAttribute(SessionKeys.USER_ID, 7L);

        doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("[POST]")
                .contains("/api/v1/auction-items/42/bids")
                .contains("200")
                .contains("userId=7");
    }

    @Test
    @DisplayName("5xx 는 error 가 아니라 warn 으로 남겨 슬랙 알림이 두 번 가지 않게 한다")
    void writesServerErrorAsWarn() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failing = (req, res) -> ((MockHttpServletResponse) res).setStatus(500);

        doFilter(request("GET", "/api/v1/auction-rooms"), response, failing);

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("500");
    }

    @Test
    @DisplayName("4xx 는 info 로 남긴다")
    void writesClientErrorAsInfo() throws Exception {
        FilterChain rejected = (req, res) -> ((MockHttpServletResponse) res).setStatus(400);

        doFilter(request("POST", "/api/v1/auction-items/42/bids"), new MockHttpServletResponse(), rejected);

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.INFO);
    }

    private void doFilter(MockHttpServletRequest request, MockHttpServletResponse response, FilterChain chain)
            throws Exception {
        accessLogFilter.doFilter(request, response, chain);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private ILoggingEvent onlyEvent() {
        List<ILoggingEvent> events = captured.list;
        assertThat(events).hasSize(1);
        return events.get(0);
    }
}
