package com.hot6ix.upbid.global.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * {@code logback-spring.xml} 이 prod 에서 실제로 SLACK appender 를 붙이는지 본다.
 *
 * <p>단위 테스트는 appender 객체를 직접 만들어 쓰기 때문에, XML 이 깨져 있거나
 * {@code <springProperty>} 가 값을 못 넣어도 전부 통과한다. 그러면 운영에 나가서야
 * "알림이 하나도 안 온다"를 알게 된다. <b>여기가 그 구멍을 막는 자리다.</b>
 */
@SpringBootTest(properties = {
        "app.alert.slack.webhook-url=http://127.0.0.1:9/hook",
        "app.alert.slack.cooldown-seconds=30"
})
@ActiveProfiles("prod")
class SlackAppenderWiringTest extends AbstractMySqlContainerTest {

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

    @Test
    @DisplayName("prod 로 뜨면 root 로거에 SLACK appender 가 붙고 설정값이 들어간다")
    void attachesSlackAppenderOnProdProfile() {
        Appender<?> appender = rootLogger().getAppender("SLACK");

        assertThat(appender)
                .as("logback-spring.xml 의 prod 블록이 SLACK appender 를 붙여야 한다")
                .isInstanceOf(SlackAppender.class);

        assertThat(appender.isStarted())
                .as("webhook-url 이 springProperty 로 들어와야 appender 가 켜진다")
                .isTrue();
    }

    @Test
    @DisplayName("콘솔 출력은 그대로 살아 있다")
    void keepsConsoleAppender() {
        assertThat(rootLogger().getAppender("CONSOLE")).isNotNull();
    }

    @Test
    @DisplayName("loki 프로파일을 안 켜면 Loki appender 가 붙지 않는다")
    void doesNotAttachLokiWithoutProfile() {
        // 기본값이 꺼짐이라는 것이 이 설계의 전제다. prod 만으로 배포했을 때 없던 전송이
        // 생기면 안 되고, Loki 가 아직 안 떴는데 켜져 있으면 전송 실패 로그만 쌓인다.
        assertThat(rootLogger().getAppender("LOKI")).isNull();
    }

    private Logger rootLogger() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return context.getLogger(Logger.ROOT_LOGGER_NAME);
    }
}
