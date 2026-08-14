package com.hot6ix.upbid.domain.auth.sms.store;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * {@link VerificationCodeStore}의 Redis 구현체.
 *
 * <p>엔트리는 Hash로 저장한다({@code code}/{@code issuedAt}/{@code expiresAt}/{@code failCount}
 * 필드 분리) — {@code failCount} 증가를 {@code HINCRBY}로 원자적으로 처리하기 위해서다. 하나의
 * 값을 JSON으로 통째로 저장하면 증가가 read-modify-write가 되어 동시 검증 시도에서 lost update가
 * 생길 수 있다.
 *
 * <p>Redis TTL은 판정 기준이 아니라 안전망이다. {@code expiresAt} 필드로 서비스가 직접 만료를
 * 판정해야 {@code CODE_EXPIRED}(발급됐지만 만료)와 {@code CODE_NOT_FOUND}(발급 안 됨)를 구분하는
 * 지금 에러 응답이 유지된다. TTL만 믿으면 만료 순간 Redis가 키를 지워버려서 항상 CODE_NOT_FOUND로
 * 뭉개진다. 그래서 TTL은 {@code expiresAt}보다 {@link #TTL_BUFFER}만큼 더 여유 있게 건다.
 *
 * <p>발송 이력은 Sorted Set(점수=발송 시각)으로 저장해, 인메모리 구현의 슬라이딩 윈도우(최근 N시간
 * 필터링)와 동일한 정확도를 유지한다. 고정 시간 버킷(예: 시간대별 카운터)은 구현이 더 간단하지만
 * 경계에서 부정확해진다(예: 10:59에 5번+11:01에 5번 = 2분 사이 10번인데 통과됨).
 *
 * <p>일일 총 발송량은 날짜별 키({@code sms:daily-total:yyyy-MM-dd}) + TTL로 자연 만료시킨다.
 * 고정 키 + 자정 스케줄러 리셋 방식은 스케줄러가 그 순간 정상 동작해야 한다는 의존성이 남는다.
 *
 * <p>엔트리 Hash·발송 이력 Sorted Set·일일 카운터 모두 TTL로 자연 정리되므로, 인메모리 구현에
 * 있던 자정 청소 스케줄러({@code evictExpiredEntries})에 대응하는 로직이 따로 필요 없다.
 */
@Component
@RequiredArgsConstructor
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final String CODE_KEY_PREFIX = "sms:code:";
    private static final String SEND_HISTORY_KEY_PREFIX = "sms:send-history:";
    private static final String DAILY_TOTAL_KEY_PREFIX = "sms:daily-total:";

    private static final String FIELD_CODE = "code";
    private static final String FIELD_ISSUED_AT = "issuedAt";
    private static final String FIELD_EXPIRES_AT = "expiresAt";
    private static final String FIELD_FAIL_COUNT = "failCount";

    /** entry의 expiresAt이 지난 뒤에도 이 시간만큼은 Redis에 더 남겨둔다(CODE_EXPIRED 판정용). */
    private static final Duration TTL_BUFFER = Duration.ofMinutes(1);

    /** 발송 이력을 슬라이딩 윈도우로 보관하는 기간. countSendsWithinDay(24시간)를 넘는 항목은 의미가 없다. */
    private static final Duration SEND_HISTORY_WINDOW = Duration.ofDays(1);

    /** 일일 총 발송량 키의 TTL. 하루(24h)보다 여유를 둬 자정 경계에서 조회가 씹히지 않게 한다. */
    private static final Duration DAILY_TOTAL_TTL = Duration.ofHours(25);

    private static final DateTimeFormatter DATE_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final ZoneId ZONE = ZoneId.systemDefault();

    /**
     * 키가 있을 때만 failCount를 증가시킨다. HINCRBY만 쓰면 키가 없을 때도 필드 하나짜리 Hash를
     * 새로 만들어버려서(다른 필드가 없는 깨진 엔트리), EXISTS 확인과 HINCRBY를 하나의 원자적
     * 연산으로 묶어야 한다.
     */
    private static final RedisScript<Long> INCREMENT_FAIL_COUNT_IF_EXISTS = RedisScript.of(
            "if redis.call('EXISTS', KEYS[1]) == 1 then "
                    + "return redis.call('HINCRBY', KEYS[1], ARGV[1], 1) "
                    + "end "
                    + "return nil",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String phoneNumber, VerificationEntry entry) {
        String key = codeKey(phoneNumber);

        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        hashOps.putAll(key, Map.of(
                FIELD_CODE, entry.code(),
                FIELD_ISSUED_AT, String.valueOf(toEpochMilli(entry.issuedAt())),
                FIELD_EXPIRES_AT, String.valueOf(toEpochMilli(entry.expiresAt())),
                FIELD_FAIL_COUNT, String.valueOf(entry.failCount())
        ));

        Duration ttl = Duration.between(LocalDateTime.now(), entry.expiresAt()).plus(TTL_BUFFER);
        redisTemplate.expire(key, ttl.isNegative() ? TTL_BUFFER : ttl);
    }

    @Override
    public Optional<VerificationEntry> find(String phoneNumber) {
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        Map<String, String> fields = hashOps.entries(codeKey(phoneNumber));

        if (fields.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new VerificationEntry(
                fields.get(FIELD_CODE),
                toLocalDateTime(Long.parseLong(fields.get(FIELD_ISSUED_AT))),
                toLocalDateTime(Long.parseLong(fields.get(FIELD_EXPIRES_AT))),
                Integer.parseInt(fields.get(FIELD_FAIL_COUNT))
        ));
    }

    @Override
    public void delete(String phoneNumber) {
        redisTemplate.delete(codeKey(phoneNumber));
    }

    @Override
    public void incrementFailCount(String phoneNumber) {
        redisTemplate.execute(INCREMENT_FAIL_COUNT_IF_EXISTS, List.of(codeKey(phoneNumber)), FIELD_FAIL_COUNT);
    }

    @Override
    public void recordSend(String phoneNumber) {
        String historyKey = sendHistoryKey(phoneNumber);
        long nowMillis = System.currentTimeMillis();

        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(historyKey, UUID.randomUUID().toString(), nowMillis);
        zSetOps.removeRangeByScore(historyKey, Double.NEGATIVE_INFINITY, nowMillis - SEND_HISTORY_WINDOW.toMillis());
        redisTemplate.expire(historyKey, SEND_HISTORY_WINDOW);

        String totalKey = dailyTotalKey();
        Long total = redisTemplate.opsForValue().increment(totalKey);
        if (total != null && total == 1L) {
            redisTemplate.expire(totalKey, DAILY_TOTAL_TTL);
        }
    }

    @Override
    public Optional<LocalDateTime> getLastSentAt(String phoneNumber) {
        Set<ZSetOperations.TypedTuple<String>> latest =
                redisTemplate.opsForZSet().reverseRangeWithScores(sendHistoryKey(phoneNumber), 0, 0);

        if (latest == null || latest.isEmpty()) {
            return Optional.empty();
        }

        Double score = latest.iterator().next().getScore();
        return score == null ? Optional.empty() : Optional.of(toLocalDateTime(score.longValue()));
    }

    @Override
    public long countSendsWithinHour(String phoneNumber) {
        return countSince(phoneNumber, Duration.ofHours(1));
    }

    @Override
    public long countSendsWithinDay(String phoneNumber) {
        return countSince(phoneNumber, Duration.ofDays(1));
    }

    @Override
    public long countTotalSendsToday() {
        String value = redisTemplate.opsForValue().get(dailyTotalKey());
        return value == null ? 0 : Long.parseLong(value);
    }

    private long countSince(String phoneNumber, Duration window) {
        long since = System.currentTimeMillis() - window.toMillis();
        Long count = redisTemplate.opsForZSet()
                .count(sendHistoryKey(phoneNumber), since, Double.POSITIVE_INFINITY);
        return count == null ? 0 : count;
    }

    private String codeKey(String phoneNumber) {
        return CODE_KEY_PREFIX + phoneNumber;
    }

    private String sendHistoryKey(String phoneNumber) {
        return SEND_HISTORY_KEY_PREFIX + phoneNumber;
    }

    private String dailyTotalKey() {
        return DAILY_TOTAL_KEY_PREFIX + LocalDate.now(ZONE).format(DATE_KEY_FORMATTER);
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZONE).toInstant().toEpochMilli();
    }

    private LocalDateTime toLocalDateTime(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZONE);
    }
}
