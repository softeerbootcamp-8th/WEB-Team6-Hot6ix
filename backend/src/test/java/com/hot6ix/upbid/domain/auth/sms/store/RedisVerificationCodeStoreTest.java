package com.hot6ix.upbid.domain.auth.sms.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * {@link RedisVerificationCodeStore}가 실제 Redis를 상대로 정확히 동작하는지 확인한다.
 * Spring 컨텍스트 없이 {@code RedisLockProviderTest}와 같은 방식으로 GenericContainer를
 * 직접 띄운다.
 */
class RedisVerificationCodeStoreTest {

    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.0")).withExposedPorts(6379);

    private static final String PHONE_NUMBER = "01012345678";

    /** {@link RedisVerificationCodeStore}가 내부적으로 쓰는 발송 이력 키와 같은 규칙. */
    private static final String SEND_HISTORY_KEY = "sms:send-history:" + PHONE_NUMBER;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisVerificationCodeStore store;

    static {
        REDIS_CONTAINER.start();
    }

    @BeforeAll
    static void setUpStore() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        store = new RedisVerificationCodeStore(redisTemplate);
    }

    @AfterAll
    static void tearDownStore() {
        connectionFactory.destroy();
    }

    /** 테스트마다 컨테이너를 새로 띄우는 대신, 매번 비워서 독립성을 확보한다. */
    @AfterEach
    void flushRedis() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    private VerificationEntry newEntry(String code) {
        return new VerificationEntry(code, LocalDateTime.now(), LocalDateTime.now().plusMinutes(4), 0);
    }

    @Nested
    @DisplayName("save / find / delete")
    class SaveFindDelete {

        @Test
        @DisplayName("저장한 엔트리를 조회할 수 있다")
        void save_그리고_find() {
            VerificationEntry entry = newEntry("123456");
            store.save(PHONE_NUMBER, entry);

            Optional<VerificationEntry> found = store.find(PHONE_NUMBER);

            assertThat(found).isPresent();
            assertThat(found.get().code()).isEqualTo("123456");
            assertThat(found.get().failCount()).isZero();
            assertThat(found.get().issuedAt().truncatedTo(ChronoUnit.MILLIS))
                    .isEqualTo(entry.issuedAt().truncatedTo(ChronoUnit.MILLIS));
            assertThat(found.get().expiresAt().truncatedTo(ChronoUnit.MILLIS))
                    .isEqualTo(entry.expiresAt().truncatedTo(ChronoUnit.MILLIS));
        }

        @Test
        @DisplayName("새 인증번호를 저장하면 기존 엔트리가 덮어써진다")
        void save_기존_엔트리_덮어쓰기() {
            store.save(PHONE_NUMBER, newEntry("111111"));
            store.save(PHONE_NUMBER, newEntry("999999"));

            assertThat(store.find(PHONE_NUMBER).get().code()).isEqualTo("999999");
        }

        @Test
        @DisplayName("없는 번호를 조회하면 빈 Optional을 반환한다")
        void find_없는_번호() {
            assertThat(store.find(PHONE_NUMBER)).isEmpty();
        }

        @Test
        @DisplayName("삭제 후 조회하면 빈 Optional을 반환한다")
        void delete_후_find() {
            store.save(PHONE_NUMBER, newEntry("123456"));
            store.delete(PHONE_NUMBER);

            assertThat(store.find(PHONE_NUMBER)).isEmpty();
        }
    }

    @Nested
    @DisplayName("incrementFailCount")
    class IncrementFailCount {

        @Test
        @DisplayName("실패 횟수를 증가시키면 저장된 엔트리의 failCount가 1 늘어난다")
        void incrementFailCount_실패_횟수_증가() {
            store.save(PHONE_NUMBER, newEntry("123456"));
            store.incrementFailCount(PHONE_NUMBER);

            assertThat(store.find(PHONE_NUMBER).get().failCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("동시에 두 번 증가시켜도 HINCRBY의 원자성 덕분에 둘 다 반영된다")
        void incrementFailCount_동시_증가_원자성() throws InterruptedException {
            store.save(PHONE_NUMBER, newEntry("123456"));

            Thread first = new Thread(() -> store.incrementFailCount(PHONE_NUMBER));
            Thread second = new Thread(() -> store.incrementFailCount(PHONE_NUMBER));
            first.start();
            second.start();
            first.join();
            second.join();

            assertThat(store.find(PHONE_NUMBER).get().failCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("존재하지 않는 번호의 실패 횟수를 증가시켜도 예외가 없고, 깨진 엔트리도 생기지 않는다")
        void incrementFailCount_없는_번호() {
            store.incrementFailCount(PHONE_NUMBER); // 예외 없이 무시

            assertThat(store.find(PHONE_NUMBER)).isEmpty();
        }
    }

    @Nested
    @DisplayName("recordSend / countSendsWithinHour / countSendsWithinDay / getLastSentAt")
    class RecordSend {

        @Test
        @DisplayName("발송을 기록하면 1시간 내 발송 횟수에 반영된다")
        void recordSend_시간당_횟수_반영() {
            store.recordSend(PHONE_NUMBER);
            store.recordSend(PHONE_NUMBER);

            assertThat(store.countSendsWithinHour(PHONE_NUMBER)).isEqualTo(2);
        }

        @Test
        @DisplayName("발송을 기록하면 하루 발송 횟수에 반영된다")
        void recordSend_일일_횟수_반영() {
            store.recordSend(PHONE_NUMBER);
            store.recordSend(PHONE_NUMBER);
            store.recordSend(PHONE_NUMBER);

            assertThat(store.countSendsWithinDay(PHONE_NUMBER)).isEqualTo(3);
        }

        @Test
        @DisplayName("발송 이력이 없는 번호의 횟수는 0이다")
        void count_발송_이력_없음() {
            assertThat(store.countSendsWithinHour(PHONE_NUMBER)).isZero();
            assertThat(store.countSendsWithinDay(PHONE_NUMBER)).isZero();
        }

        @Test
        @DisplayName("1시간이 지난 발송 이력은 시간당 횟수에서 제외된다")
        void countSendsWithinHour_1시간_이전_항목_제외() {
            long twoHoursAgo = System.currentTimeMillis() - Duration.ofHours(2).toMillis();
            redisTemplate.opsForZSet().add(SEND_HISTORY_KEY, UUID.randomUUID().toString(), twoHoursAgo);
            store.recordSend(PHONE_NUMBER); // 현재 → 포함 대상

            assertThat(store.countSendsWithinHour(PHONE_NUMBER)).isEqualTo(1);
        }

        @Test
        @DisplayName("24시간이 지난 발송 이력은 일일 횟수에서 제외된다")
        void countSendsWithinDay_24시간_이전_항목_제외() {
            long twoDaysAgo = System.currentTimeMillis() - Duration.ofDays(2).toMillis();
            redisTemplate.opsForZSet().add(SEND_HISTORY_KEY, UUID.randomUUID().toString(), twoDaysAgo);
            store.recordSend(PHONE_NUMBER); // 현재 → 포함 대상

            assertThat(store.countSendsWithinDay(PHONE_NUMBER)).isEqualTo(1);
        }

        @Test
        @DisplayName("가장 최근 발송 시각을 반환한다")
        void getLastSentAt_최근_발송_시각() {
            store.recordSend(PHONE_NUMBER);

            assertThat(store.getLastSentAt(PHONE_NUMBER)).isPresent();
        }

        @Test
        @DisplayName("발송 이력이 없으면 빈 Optional을 반환한다")
        void getLastSentAt_이력_없음() {
            assertThat(store.getLastSentAt(PHONE_NUMBER)).isEmpty();
        }
    }

    @Nested
    @DisplayName("countTotalSendsToday")
    class CountTotalSendsToday {

        @Test
        @DisplayName("발송할 때마다 서비스 전체 발송 건수가 증가한다")
        void countTotalSendsToday_증가() {
            store.recordSend(PHONE_NUMBER);
            store.recordSend("01099998888");

            assertThat(store.countTotalSendsToday()).isEqualTo(2);
        }

        @Test
        @DisplayName("발송 이력이 없으면 0이다")
        void countTotalSendsToday_이력_없음() {
            assertThat(store.countTotalSendsToday()).isZero();
        }
    }
}
