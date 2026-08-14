package com.hot6ix.upbid.global.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.scheduler.AuctionClosePoller;
import com.hot6ix.upbid.domain.auction.scheduler.ItemClosingSoonPoller;
import com.hot6ix.upbid.global.redis.RateLimiterRedisClient;
import com.hot6ix.upbid.global.redis.RedisDelayQueue;
import java.util.Arrays;
import java.util.concurrent.Executor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 애플리케이션 컨텍스트가 실제로 뜨는지 본다. <b>다른 테스트는 전부 클래스를 직접 만들거나
 * 슬라이스만 띄워서, 빈 배선이 틀려도 빌드가 통과한다.</b>
 *
 * <p>실제로 그 구멍에 빠진 적이 있다. 마감 실행용 {@code Executor} 빈을 추가했더니 Boot 가
 * {@code applicationTaskExecutor} 를 안 만들었는데({@code @ConditionalOnMissingBean(Executor.class)}),
 * 테스트는 전부 통과했고 앱을 띄워보고서야 찾았다. SSE 가 그 실행자를 타기 때문에 그대로
 * 나갔으면 연결마다 스레드가 새로 생길 뻔했다. <b>이 테스트가 그걸 잡는다.</b>
 *
 * <p>외부 연동 설정은 값만 채워 준다. 컨텍스트를 띄우는 데 필요할 뿐 실제로 호출하지 않는다.
 */
@SpringBootTest
class ApplicationContextLoadTest extends AbstractMySqlContainerTest {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.0")).withExposedPorts(6379);

    static {
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void externalCredentials(DynamicPropertyRegistry registry) {
        registry.add("kakao.client-id", () -> "test");
        registry.add("kakao.redirect-uri", () -> "http://localhost/test");
        registry.add("ncp.sms.access-key", () -> "test");
        registry.add("ncp.sms.secret-key", () -> "test");
        registry.add("ncp.sms.service-id", () -> "test");
        registry.add("ncp.sms.send-from", () -> "01000000000");
    }

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * <b>컨텍스트가 떴다는 것 자체가 큐 배선 검증이다.</b> {@code RedisDelayQueue} 는 마감용과
     * 알림용으로 빈이 둘이라 타입만으로는 안 갈리고 <b>필드 이름</b>으로 갈리는데, 이름이
     * 어긋나면 주입 단계에서 컨텍스트가 아예 안 뜬다.
     */
    @Test
    @DisplayName("컨텍스트가 뜨고 마감·알림 예약에 필요한 빈이 다 붙는다")
    void contextLoadsWithSchedulingBeans() {
        assertThat(applicationContext.getBean("closeDelayQueue", RedisDelayQueue.class)).isNotNull();
        assertThat(applicationContext.getBean("closingSoonDelayQueue", RedisDelayQueue.class))
                .isNotNull();
        assertThat(applicationContext.getBean(AuctionClosePoller.class)).isNotNull();
        assertThat(applicationContext.getBean(ItemClosingSoonPoller.class)).isNotNull();
        assertThat(applicationContext.getBean("auctionCloseExecutor", Executor.class)).isNotNull();
    }

    @Test
    @DisplayName("마감 전용 Executor 를 추가해도 Boot 기본 실행자가 살아 있다")
    void applicationTaskExecutorSurvivesCustomExecutor() {
        // 이게 없어지면 Spring MVC 비동기(SSE)가 요청마다 스레드를 새로 만드는 기본값으로 떨어진다.
        assertThat(applicationContext.containsBean("applicationTaskExecutor")).isTrue();
    }

    /**
     * {@code RateLimiterRedisClient}가 자기 커넥션을 빈으로 노출하면 Boot 의
     * {@code @ConditionalOnMissingBean(RedisConnectionFactory.class)}/{@code (StringRedisTemplate.class)}
     * 자동설정이 통째로 물러난다. 그러면 이 테스트가 잡는 마감 예약 빈들이 전부 그 리미터
     * 전용 설정(200ms 타임아웃)으로 갈아타 버린다 — 컨텍스트는 여전히 뜨므로 다른 테스트로는
     * 못 잡고, 빈 이름과 개수를 직접 세어야 한다.
     */
    @Test
    @DisplayName("리미터 전용 Redis 커넥션이 기본 자동설정 빈을 밀어내지 않는다")
    void rateLimiterConnectionDoesNotReplaceDefaultRedisBeans() {

        assertThat(applicationContext.getBeanNamesForType(RedisConnectionFactory.class))
                .containsExactly("redisConnectionFactory");

        assertThat(applicationContext.containsBean("stringRedisTemplate")).isTrue();

        assertThat(applicationContext.getBeanNamesForType(StringRedisTemplate.class))
                .as("기본 stringRedisTemplate 빈 하나만 노출되어야 한다 (실제 참조: %s)",
                        Arrays.toString(applicationContext.getBeanNamesForType(StringRedisTemplate.class)))
                .containsExactly("stringRedisTemplate");

        assertThat(applicationContext.getBean(RateLimiterRedisClient.class)).isNotNull();
    }
}
