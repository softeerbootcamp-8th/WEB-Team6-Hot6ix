package com.hot6ix.upbid.global.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.scheduler.AuctionClosePoller;
import com.hot6ix.upbid.global.redis.RedisDelayQueue;
import java.util.concurrent.Executor;
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

    @Test
    @DisplayName("컨텍스트가 뜨고 마감 예약에 필요한 빈이 다 붙는다")
    void contextLoadsWithCloseSchedulingBeans() {
        assertThat(applicationContext.getBean(RedisDelayQueue.class)).isNotNull();
        assertThat(applicationContext.getBean(AuctionClosePoller.class)).isNotNull();
        assertThat(applicationContext.getBean("auctionCloseExecutor", Executor.class)).isNotNull();
    }

    @Test
    @DisplayName("마감 전용 Executor 를 추가해도 Boot 기본 실행자가 살아 있다")
    void applicationTaskExecutorSurvivesCustomExecutor() {
        // 이게 없어지면 Spring MVC 비동기(SSE)가 요청마다 스레드를 새로 만드는 기본값으로 떨어진다.
        assertThat(applicationContext.containsBean("applicationTaskExecutor")).isTrue();
    }
}
