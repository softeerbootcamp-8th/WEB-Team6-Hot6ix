package com.hot6ix.upbid.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final TraceIdFilter traceIdFilter = new TraceIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("요청을 처리하는 동안 MDC에서 traceId를 읽을 수 있다")
    void putsTraceIdIntoMdcDuringRequest() throws ServletException, IOException {
        String[] traceIdInChain = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                traceIdInChain[0] = MDC.get(TraceIdFilter.TRACE_ID);
            }
        };

        traceIdFilter.doFilter(request, response, chain);

        assertThat(traceIdInChain[0]).isNotNull().hasSize(8);
    }

    @Test
    @DisplayName("같은 traceId를 X-Trace-Id 응답 헤더로 내보낸다")
    void writesTraceIdToResponseHeader() throws ServletException, IOException {
        String[] traceIdInChain = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                traceIdInChain[0] = MDC.get(TraceIdFilter.TRACE_ID);
            }
        };

        traceIdFilter.doFilter(request, response, chain);

        assertThat(response.getHeader(TRACE_ID_HEADER)).isEqualTo(traceIdInChain[0]);
    }

    @Test
    @DisplayName("요청마다 다른 traceId를 만든다")
    void generatesDifferentTraceIdPerRequest() throws ServletException, IOException {
        MockHttpServletResponse another = new MockHttpServletResponse();

        traceIdFilter.doFilter(request, response, new MockFilterChain());
        traceIdFilter.doFilter(new MockHttpServletRequest(), another, new MockFilterChain());

        assertThat(response.getHeader(TRACE_ID_HEADER))
                .isNotEqualTo(another.getHeader(TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("요청이 끝나면 MDC를 비운다")
    void clearsMdcAfterRequest() throws ServletException, IOException {

        traceIdFilter.doFilter(request, response, new MockFilterChain());

        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("요청 처리 중 예외가 나도 MDC를 비운다")
    void clearsMdcWhenRequestFails() {
        MockFilterChain failing = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalStateException("처리 실패");
            }
        };

        assertThatThrownBy(() -> traceIdFilter.doFilter(request, response, failing))
                .isInstanceOf(IllegalStateException.class);

        // 톰캣이 스레드를 재사용하므로 여기서 남으면 다음 요청 로그에 이전 traceId가 섞인다
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }
}
