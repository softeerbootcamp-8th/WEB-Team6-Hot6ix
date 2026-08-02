package com.hot6ix.upbid.domain.auth.sms.store;

import java.time.LocalDateTime;

/**
 * 인증번호 발급 정보를 담는 불변 VO.
 *
 * <p>record이므로 상태 변경 시 새 인스턴스를 반환한다.
 * ({@link #incrementFailCount()} 참고)
 *
 * @param code      6자리 숫자 인증번호
 * @param issuedAt  발급 시각 (재발송 쿨다운 계산 기준)
 * @param expiresAt 실제 만료 시각 (발급 후 4분, SMS 지연 대응을 위해 UI 표시 3분보다 1분 여유)
 * @param failCount 현재까지의 인증 실패 횟수
 */
public record VerificationEntry(
        String code,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        int failCount
) {
    /**
     * 현재 시각이 만료 시각을 지났으면 true를 반환한다.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 실패 횟수를 1 증가시킨 새 VerificationEntry를 반환한다.
     * record는 불변이므로 기존 인스턴스는 변경되지 않는다.
     */
    public VerificationEntry incrementFailCount() {
        return new VerificationEntry(code, issuedAt, expiresAt, failCount + 1);
    }
}
