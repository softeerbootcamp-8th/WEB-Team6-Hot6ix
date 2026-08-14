package com.hot6ix.upbid.domain.auth.sms.store;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 인증번호 저장소 인터페이스.
 *
 * <p>구현체는 {@link RedisVerificationCodeStore}(Redis)다. 여러 인스턴스가
 * 인증번호·발송 이력을 공유해야 해서 인메모리 구현은 쓰지 않는다.
 *
 * <p>저장소가 관리하는 데이터:
 * <ul>
 *   <li>{@code store} — 전화번호별 현재 유효한 인증번호 엔트리</li>
 *   <li>{@code sendHistory} — 전화번호별 발송 이력 (횟수 제한 계산용)</li>
 *   <li>{@code dailyTotalCount} — 당일 전체 발송 건수 (과금 방어용 총량 제한)</li>
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

    /** 해당 전화번호의 가장 최근 발송 시각을 반환한다. 발송 이력이 없으면 빈 Optional을 반환한다. */
    Optional<LocalDateTime> getLastSentAt(String phoneNumber);

    /** 최근 1시간 내 해당 전화번호로 발송된 횟수를 반환한다. */
    long countSendsWithinHour(String phoneNumber);

    /** 최근 24시간 내 해당 전화번호로 발송된 횟수를 반환한다. */
    long countSendsWithinDay(String phoneNumber);

    /** 당일 서비스 전체 발송 건수를 반환한다. */
    long countTotalSendsToday();
}
