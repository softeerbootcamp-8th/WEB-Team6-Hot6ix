package com.hot6ix.upbid.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * {@link SchedulingConfig#lockProvider}가 Redis 락을 실제로 배타적으로 잡는지 검증한다.
 * Spring 컨텍스트 없이 {@link RedisLockProvider}를 직접 구성한다 — 이 테스트가 보는 것은
 * ShedLock·Redis 조합의 동작이라 컨텍스트 로딩까지 필요하지 않다.
 */
class RedisLockProviderTest {

    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.0")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static LockProvider lockProvider;

    static {
        REDIS_CONTAINER.start();
    }

    @BeforeAll
    static void setUpLockProvider() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();

        lockProvider = new RedisLockProvider(connectionFactory, "upbid");
    }

    @AfterAll
    static void tearDownLockProvider() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("같은 이름의 락을 두 번 잡으려 하면 이미 잡혀 있는 두 번째 시도는 빈 Optional을 받는다")
    void secondLockAttemptFailsWhileFirstIsHeld() {
        LockConfiguration lockConfiguration =
                new LockConfiguration(Instant.now(), "award-recovery", Duration.ofMinutes(10), Duration.ofMinutes(5));

        Optional<SimpleLock> firstLock = lockProvider.lock(lockConfiguration);
        Optional<SimpleLock> secondLock = lockProvider.lock(lockConfiguration);

        assertThat(firstLock).isPresent();
        assertThat(secondLock).isEmpty();

        firstLock.get().unlock();
    }
}
