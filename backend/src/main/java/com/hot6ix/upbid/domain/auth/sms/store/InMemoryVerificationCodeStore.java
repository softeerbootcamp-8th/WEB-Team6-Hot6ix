package com.hot6ix.upbid.domain.auth.sms.store;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link VerificationCodeStore}의 인메모리 구현체.
 *
 * <p>단일 인스턴스 환경을 전제로 하며, 다중 서버 환경으로 확장 시
 * Redis 기반 구현체로 교체한다.
 *
 * <p>동시성 처리:
 * <ul>
 *   <li>{@code store} — ConcurrentHashMap으로 원자적 읽기·쓰기 보장</li>
 *   <li>{@code sendHistory} — ConcurrentHashMap + CopyOnWriteArrayList 조합으로
 *       다중 스레드에서 발송 이력 추가 시 안전하게 동작</li>
 * </ul>
 */
@Component
public class InMemoryVerificationCodeStore implements VerificationCodeStore {

    // 전화번호 → 현재 유효한 인증번호 엔트리
    private final Map<String, VerificationEntry> store = new ConcurrentHashMap<>();

    // 전화번호 → 발송 시각 목록 (1시간·하루 발송 횟수 제한 계산용)
    private final Map<String, CopyOnWriteArrayList<LocalDateTime>> sendHistory = new ConcurrentHashMap<>();

    @Override
    public void save(String phoneNumber, VerificationEntry entry) {
        store.put(phoneNumber, entry);
    }

    @Override
    public Optional<VerificationEntry> find(String phoneNumber) {
        return Optional.ofNullable(store.get(phoneNumber));
    }

    @Override
    public void delete(String phoneNumber) {
        store.remove(phoneNumber);
    }

    /**
     * 실패 횟수를 원자적으로 증가시킨다.
     * computeIfPresent는 키가 없으면 아무것도 하지 않으므로
     * 이미 삭제된 엔트리에 대한 증가는 무시된다.
     */
    @Override
    public void incrementFailCount(String phoneNumber) {
        store.computeIfPresent(phoneNumber, (k, entry) -> entry.incrementFailCount());
    }

    @Override
    public void recordSend(String phoneNumber) {
        // computeIfAbsent로 리스트 생성과 추가를 원자적으로 처리
        sendHistory.computeIfAbsent(phoneNumber, k -> new CopyOnWriteArrayList<>())
                .add(LocalDateTime.now());
    }

    @Override
    public long countSendsWithinHour(String phoneNumber) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        return getHistory(phoneNumber).stream()
                .filter(t -> t.isAfter(oneHourAgo))
                .count();
    }

    @Override
    public long countSendsWithinDay(String phoneNumber) {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        return getHistory(phoneNumber).stream()
                .filter(t -> t.isAfter(oneDayAgo))
                .count();
    }

    private CopyOnWriteArrayList<LocalDateTime> getHistory(String phoneNumber) {
        return sendHistory.getOrDefault(phoneNumber, new CopyOnWriteArrayList<>());
    }

    /**
     * 매일 자정에 24시간이 지난 발송 이력을 정리한다.
     *
     * <p>일별 발송 제한(10회)이 24시간 기준이므로, 그보다 오래된 이력은
     * 횟수 계산에 영향을 주지 않아 제거해도 안전하다.
     * 이력이 비어진 전화번호는 맵에서도 제거해 메모리 누수를 방지한다.
     *
     * <p>인증코드 store는 인증 성공·실패 시 즉시 삭제되므로 별도 정리가 불필요하다.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void evictSendHistory() {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        sendHistory.forEach((phone, times) -> times.removeIf(t -> t.isBefore(oneDayAgo)));
        sendHistory.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
