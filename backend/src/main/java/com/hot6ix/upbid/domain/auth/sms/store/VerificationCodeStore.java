package com.hot6ix.upbid.domain.auth.sms.store;

import java.util.Optional;

/**
 * 인증번호 저장소 인터페이스.
 *
 * <p>현재 구현체는 {@link InMemoryVerificationCodeStore}(인메모리)이며,
 * 추후 Redis 도입 시 이 인터페이스의 구현체만 교체하면 된다.
 *
 * <p>저장소가 관리하는 데이터:
 * <ul>
 *   <li>{@code store} — 전화번호별 현재 유효한 인증번호 엔트리</li>
 *   <li>{@code sendHistory} — 전화번호별 발송 이력 (횟수 제한 계산용)</li>
 * </ul>
 */
public interface VerificationCodeStore {

    /** 전화번호에 인증번호 엔트리를 저장한다. 기존 엔트리가 있으면 덮어쓴다. */
    void save(String phoneNumber, VerificationEntry entry);

    /** 전화번호에 해당하는 인증번호 엔트리를 조회한다. */
    Optional<VerificationEntry> find(String phoneNumber);

    /** 전화번호에 해당하는 인증번호 엔트리를 삭제한다. */
    void delete(String phoneNumber);

    /** 해당 전화번호의 인증 실패 횟수를 1 증가시킨다. */
    void incrementFailCount(String phoneNumber);

    /** 현재 시각을 해당 전화번호의 발송 이력에 기록한다. */
    void recordSend(String phoneNumber);

    /** 최근 1시간 내 해당 전화번호로 발송된 횟수를 반환한다. */
    long countSendsWithinHour(String phoneNumber);

    /** 최근 24시간 내 해당 전화번호로 발송된 횟수를 반환한다. */
    long countSendsWithinDay(String phoneNumber);
}
