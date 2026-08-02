package com.hot6ix.upbid.domain.auth.sms.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationEntryTest {

    @Test
    @DisplayName("만료 시각이 현재보다 이전이면 만료된 것으로 판단한다")
    void isExpired_만료된_엔트리() {
        VerificationEntry entry = new VerificationEntry(
                "123456",
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().minusMinutes(1),
                0
        );

        assertThat(entry.isExpired()).isTrue();
    }

    @Test
    @DisplayName("만료 시각이 현재보다 이후이면 만료되지 않은 것으로 판단한다")
    void isExpired_유효한_엔트리() {
        VerificationEntry entry = new VerificationEntry(
                "123456",
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(3),
                0
        );

        assertThat(entry.isExpired()).isFalse();
    }

    @Test
    @DisplayName("실패 횟수를 증가시키면 기존 인스턴스는 변경되지 않고 새 인스턴스를 반환한다")
    void incrementFailCount_불변성_보장() {
        VerificationEntry original = new VerificationEntry(
                "123456",
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(3),
                2
        );

        VerificationEntry incremented = original.incrementFailCount();

        assertThat(original.failCount()).isEqualTo(2);
        assertThat(incremented.failCount()).isEqualTo(3);
        assertThat(incremented.code()).isEqualTo(original.code());
        assertThat(incremented.issuedAt()).isEqualTo(original.issuedAt());
        assertThat(incremented.expiresAt()).isEqualTo(original.expiresAt());
    }
}
