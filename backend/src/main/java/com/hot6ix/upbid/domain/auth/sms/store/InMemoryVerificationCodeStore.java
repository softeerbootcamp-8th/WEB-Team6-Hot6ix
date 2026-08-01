package com.hot6ix.upbid.domain.auth.sms.store;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link VerificationCodeStore}의 인메모리 구현체.
 *
 * <p>단일 인스턴스 환경을 전제로 하며, 다중 서버 환경으로 확장 시 Redis 기반 구현체로 교체한다.
 * 만료 엔트리와 일일 발송 카운터는 매일 자정 스케줄러가 정리한다.
 */
@Component
public class InMemoryVerificationCodeStore implements VerificationCodeStore {

    // 전화번호 → 현재 유효한 인증번호 엔트리
    private final Map<String, VerificationEntry> store = new ConcurrentHashMap<>();

    // 전화번호 → 발송 시각 목록 (1시간·하루 발송 횟수 제한 계산용)
    private final Map<String, CopyOnWriteArrayList<LocalDateTime>> sendHistory = new ConcurrentHashMap<>();

    // 당일 전체 발송 건수 (과금 방어용 총량 제한 계산용)
    private final AtomicLong dailyTotalCount = new AtomicLong(0);

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

    /**
     * 현재 시각을 발송 이력에 기록하고, 24시간이 지난 오래된 이력을 함께 정리한다.
     */
    @Override
    public void recordSend(String phoneNumber) {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        CopyOnWriteArrayList<LocalDateTime> times =
                sendHistory.computeIfAbsent(phoneNumber, k -> new CopyOnWriteArrayList<>());
        times.removeIf(t -> t.isBefore(oneDayAgo));
        times.add(LocalDateTime.now());
        dailyTotalCount.incrementAndGet();
    }

    @Override
    public Optional<LocalDateTime> getLastSentAt(String phoneNumber) {
        CopyOnWriteArrayList<LocalDateTime> times = getHistory(phoneNumber);
        if (times.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(times.get(times.size() - 1));
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

    @Override
    public long countTotalSendsToday() {
        return dailyTotalCount.get();
    }

    private CopyOnWriteArrayList<LocalDateTime> getHistory(String phoneNumber) {
        return sendHistory.getOrDefault(phoneNumber, new CopyOnWriteArrayList<>());
    }

    /** 매일 자정에 만료 엔트리를 정리하고 일일 발송 카운터를 리셋한다. */
    @Scheduled(cron = "0 0 0 * * *")
    public void evictExpiredEntries() {
        LocalDateTime now = LocalDateTime.now();
        store.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        dailyTotalCount.set(0);
    }
}
