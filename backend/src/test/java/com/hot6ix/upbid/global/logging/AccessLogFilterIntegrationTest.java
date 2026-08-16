package com.hot6ix.upbid.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 필터가 실제 요청 경로에 붙어 있는지 본다.
 *
 * <p>{@link AccessLogFilterTest} 는 필터를 직접 만들어 부르기 때문에, 빈 등록이나 순서가
 * 틀려도 통과한다. 여기서는 진짜 톰캣에 요청을 보내서 두 가지를 확인한다.
 *
 * <ul>
 *   <li>접근 로그가 실제로 남는가 (필터가 체인에 붙었는가)
 *   <li>그 줄에 traceId 가 실려 있는가 ({@link TraceIdFilter} 가 먼저 도는가)
 * </ul>
 *
 * <p>둘째가 중요하다. 순서가 뒤집히면 접근 로그만 traceId 없이 남아서, 정작 요청을 묶어 보려고
 * 넣은 값이 그 요청의 대표 줄에는 빠진다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccessLogFilterIntegrationTest extends AbstractMySqlContainerTest {

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

    @Value("${local.server.port}")
    private int port;

    private Logger logger;
    private ListAppender<ILoggingEvent> captured;

    @BeforeEach
    void setUp() {
        captured = new ListAppender<>();
        captured.start();

        logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
        logger.addAppender(captured);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(captured);
    }

    @Test
    @DisplayName("실제 요청이 접근 로그로 남고 그 줄에 traceId 가 실린다")
    void writesAccessLogWithTraceId() {
        get("/api/v1/auction-rooms/does-not-exist");

        assertThat(captured.list)
                .as("AccessLogFilter 가 필터 체인에 붙어 있어야 한다")
                .isNotEmpty();

        ILoggingEvent event = captured.list.get(0);

        assertThat(event.getFormattedMessage())
                .contains("[GET]")
                .contains("/api/v1/auction-rooms/does-not-exist");

        assertThat(event.getMDCPropertyMap().get(TraceIdFilter.TRACE_ID))
                .as("TraceIdFilter 가 AccessLogFilter 보다 먼저 돌아야 traceId 가 실린다")
                .isNotBlank();
    }

    @Test
    @DisplayName("액추에이터 스크랩은 실제 요청에서도 남지 않는다")
    void skipsActuatorOnRealRequest() {
        get("/actuator/health");

        assertThat(captured.list).isEmpty();
    }

    private void get(String path) {
        try {
            HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
