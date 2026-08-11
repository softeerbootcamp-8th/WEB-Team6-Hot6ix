package com.hot6ix.upbid.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DevLoginGateTest {

    private static final String TOKEN = "measurement-window-token";

    @Nested
    @DisplayName("토큰을 안 걸어 둔 실행 (local, perf)")
    class WithoutToken {

        @Test
        @DisplayName("헤더가 없어도 통과한다")
        void passesWithoutHeader() {
            assertThatCode(() -> gateOf(null).verify(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("토큰이 공백만 있어도 안 건 것으로 본다")
        void treatsBlankAsUnset() {
            assertThatCode(() -> gateOf("   ").verify(null)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("토큰을 걸어 둔 실행 (측정 창구)")
    class WithToken {

        @Test
        @DisplayName("값이 맞으면 통과한다")
        void passesWithMatchingHeader() {
            assertThatCode(() -> gateOf(TOKEN).verify(TOKEN)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("헤더가 아예 없으면 막는다")
        void rejectsMissingHeader() {
            assertThatThrownBy(() -> gateOf(TOKEN).verify(null))
                    .isInstanceOf(ApplicationException.class)
                    .hasFieldOrPropertyWithValue("errorType", AuthErrorType.UNAUTHORIZED);
        }

        @Test
        @DisplayName("값이 다르면 막는다")
        void rejectsWrongHeader() {
            assertThatThrownBy(() -> gateOf(TOKEN).verify("wrong"))
                    .isInstanceOf(ApplicationException.class);
        }

        @Test
        @DisplayName("앞자리만 맞는 값도 막는다")
        void rejectsPrefixOfToken() {
            assertThatThrownBy(() -> gateOf(TOKEN).verify(TOKEN.substring(0, 5)))
                    .isInstanceOf(ApplicationException.class);
        }
    }

    private DevLoginGate gateOf(String token) {
        return new DevLoginGate(new DevLoginProperties(token));
    }
}
