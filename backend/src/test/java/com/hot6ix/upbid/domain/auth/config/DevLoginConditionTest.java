package com.hot6ix.upbid.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

/**
 * dev-login 이 어느 실행에서 켜지는지가 곧 인증 경계라, 조건을 직접 확인한다.
 */
class DevLoginConditionTest {

    private final DevLoginCondition condition = new DevLoginCondition();

    @Test
    @DisplayName("local 은 토큰이 없어도 켠다")
    void enabledOnLocal() {
        assertThat(matches("local", null)).isTrue();
    }

    @Test
    @DisplayName("perf 는 토큰이 없어도 켠다")
    void enabledOnPerf() {
        assertThat(matches("perf", null)).isTrue();
    }

    @Test
    @DisplayName("prod 는 토큰이 없으면 빈 자체를 안 만든다")
    void disabledOnProdWithoutToken() {
        assertThat(matches("prod", null)).isFalse();
    }

    @Test
    @DisplayName("prod 에서 토큰이 공백뿐이면 안 켠다")
    void disabledOnProdWithBlankToken() {
        assertThat(matches("prod", "   ")).isFalse();
    }

    @Test
    @DisplayName("prod 도 토큰을 주면 켠다")
    void enabledOnProdWithToken() {
        assertThat(matches("prod", "measurement-window-token")).isTrue();
    }

    private boolean matches(String profile, String token) {

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);

        if (token != null) {
            environment.setProperty("upbid.dev-login.token", token);
        }

        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);

        return condition.matches(context, null);
    }
}
