package com.hot6ix.upbid.global.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.bid.ratelimit.BidRateLimiter;
import com.hot6ix.upbid.global.alert.SlackAlertService;
import com.hot6ix.upbid.global.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * {@code @WebMvcTest} 공통 부모.
 * <p>
 * {@code @WebMvcTest}는 {@code WebMvcConfigurer}와 {@code HandlerInterceptor}를 스캔 대상에 포함하므로
 * {@code WebMvcConfig} → {@code AuthInterceptor}가 함께 올라오지만, 일반 {@code @Component}인
 * {@code SessionManager}는 제외되어 컨텍스트 로딩이 실패한다. 여기서 목으로 대체하고 로그인 상태를
 * 스텁해 컨트롤러 슬라이스 테스트가 인증 인프라와 무관하게 동작하도록 한다.
 * <p>
 * 인터셉터 자체의 인증 동작은 {@code AuthInterceptor} 단위 테스트에서 검증한다.
 */
public abstract class AbstractControllerTest {

    protected static final Long LOGIN_USER_ID = 1L;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected SessionManager sessionManager;

    @MockitoBean
    protected SlackAlertService slackAlertService;

    /**
     * {@code BidRateLimitInterceptor}는 입찰 경로에만 매핑되지만, 그 인터셉터를 생성자로
     * 받는 {@code WebMvcConfig} 자체가 모든 {@code @WebMvcTest} 슬라이스에 올라오므로 목이
     * 필요하다. 기본 boolean 반환값({@code false})이 곧 "제한 없음"을 뜻해 별도 스텁 없이도
     * 통과한다.
     */
    @MockitoBean
    protected BidRateLimiter bidRateLimiter;

    @BeforeEach
    void 로그인_세션을_스텁한다() {
        when(sessionManager.findUserId(any(HttpServletRequest.class)))
                .thenReturn(Optional.of(LOGIN_USER_ID));
    }

    /**
     * 기본 스텁(로그인 상태)을 덮어써 세션이 없는 상태로 만든다.
     * {@code @GuestAllowed}가 붙은 공개 엔드포인트가 비로그인 요청을 통과시키는지 검증할 때 쓴다.
     */
    protected void 비로그인_상태로_바꾼다() {
        when(sessionManager.findUserId(any(HttpServletRequest.class)))
                .thenReturn(Optional.empty());
    }
}
