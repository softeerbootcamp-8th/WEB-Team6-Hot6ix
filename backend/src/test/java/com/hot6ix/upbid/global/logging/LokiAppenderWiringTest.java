package com.hot6ix.upbid.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import com.github.loki4j.logback.Loki4jAppender;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * {@code loki} 프로파일을 켰을 때 Loki appender 가 실제로 붙는지 본다.
 *
 * <p>{@code logback-spring.xml} 의 Loki 설정은 loki4j 2.0 의 XML 구조를 따라야 하는데 1.x 와
 * 태그가 달라서, 틀리면 <b>기동할 때 예외가 나거나 로그만 조용히 안 나간다.</b> 실제로 라벨을
 * 쉼표로 이어 썼다가 여기서 잡았다(2.0 은 줄바꿈으로 나눈다).
 *
 * <p><b>보내는 곳을 반드시 살아 있는 주소로 준다.</b> 닿지 않는 주소를 주면 loki4j 가 전송 실패를
 * logback 내부 status 에 ERROR 로 쌓고, Spring Boot 는 그걸 "logback 설정 오류" 로 보고 컨텍스트
 * 로딩을 실패시킨다. logback 컨텍스트는 JVM 전역이라 <b>무관한 다른 테스트까지 같이 깨진다</b>
 * (실제로 {@code ApplicationContextLoadTest} 가 그렇게 깨졌고 빌드가 30분 걸렸다).
 */
@SpringBootTest
@ActiveProfiles({"prod", "loki"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LokiAppenderWiringTest extends AbstractMySqlContainerTest {

    /** 로그를 받아 주기만 하는 가짜 Loki. 실제 전송이 성공해야 위 설명대로 다른 테스트가 안 깨진다. */
    private static final HttpServer LOKI_STUB;

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.0")).withExposedPorts(6379);

    static {
        REDIS_CONTAINER.start();

        try {
            LOKI_STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        LOKI_STUB.createContext("/loki/api/v1/push", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        LOKI_STUB.start();

        // 스프링이 뜨기 전에 심어야 한다. logback 은 컨텍스트가 refresh 되기 전에 초기화돼서
        // @DynamicPropertySource 로는 늦는다.
        System.setProperty("app.logging.loki.url",
                "http://127.0.0.1:" + LOKI_STUB.getAddress().getPort() + "/loki/api/v1/push");
    }

    /**
     * appender 를 전역 logback 컨텍스트에서 직접 떼어 낸다.
     *
     * <p>Spring 컨텍스트를 닫아도 이 appender 는 root 로거에 붙은 채 남는다. 그대로 두고
     * 스텁을 내리면 <b>다음 테스트 클래스의 로그가 죽은 주소로 나가면서</b> 그 컨텍스트 로딩이
     * 실패한다. 떼는 것이 먼저고 스텁을 내리는 것이 나중이다.
     */
    @AfterAll
    static void detachAppenderAndStopStub() {
        Appender<?> loki = rootLoggerStatic().getAppender("LOKI");
        if (loki != null) {
            rootLoggerStatic().detachAppender("LOKI");
            loki.stop();
        }

        LOKI_STUB.stop(0);
        System.clearProperty("app.logging.loki.url");
    }

    private static Logger rootLoggerStatic() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return context.getLogger(Logger.ROOT_LOGGER_NAME);
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
    @DisplayName("loki 프로파일을 켜면 root 로거에 LOKI appender 가 붙고 켜진다")
    void attachesLokiAppender() {
        Appender<?> appender = rootLogger().getAppender("LOKI");

        assertThat(appender)
                .as("logback-spring.xml 의 loki 블록이 파싱돼야 한다")
                .isInstanceOf(Loki4jAppender.class);

        assertThat(appender.isStarted())
                .as("http/batch/labels 태그가 loki4j 2.0 구조와 맞아야 켜진다")
                .isTrue();
    }

    @Test
    @DisplayName("콘솔과 슬랙 설정은 그대로 살아 있다")
    void keepsOtherAppenders() {
        assertThat(rootLogger().getAppender("CONSOLE")).isNotNull();
        assertThat(rootLogger().getAppender("SLACK")).isNotNull();
    }

    private Logger rootLogger() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return context.getLogger(Logger.ROOT_LOGGER_NAME);
    }
}
