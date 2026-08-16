package com.hot6ix.upbid.global.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 실제로 웹훅까지 나가는지 본다. 메시지를 만드는 부분은 {@link SlackAppenderTest} 가 본다.
 *
 * <p>웹훅 자리에 로컬 HTTP 서버를 띄워 받는다. 이 경로를 안 재면 "붙였는데 아무것도 안 온다"를
 * 운영에서 처음 알게 된다.
 */
class SlackAppenderSendTest {

    private static final String POLLER = "com.hot6ix.upbid.domain.auction.scheduler.AuctionClosePoller";

    private HttpServer webhook;
    private List<String> received;
    private CountDownLatch delivered;

    private LoggerContext loggerContext;
    private SlackAppender slackAppender;

    @BeforeEach
    void setUp() throws Exception {
        received = new CopyOnWriteArrayList<>();
        delivered = new CountDownLatch(1);

        webhook = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        webhook.createContext("/hook", exchange -> {
            try (InputStream body = exchange.getRequestBody()) {
                received.add(new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            delivered.countDown();
        });
        webhook.start();

        loggerContext = new LoggerContext();

        slackAppender = new SlackAppender();
        slackAppender.setContext(loggerContext);
        slackAppender.setWebhookUrl("http://127.0.0.1:" + webhook.getAddress().getPort() + "/hook");
        slackAppender.setCooldownSeconds(60);
        slackAppender.start();
    }

    @AfterEach
    void tearDown() {
        slackAppender.stop();
        webhook.stop(0);
    }

    @Test
    @DisplayName("ERROR 로그가 웹훅으로 나간다")
    void sendsToWebhook() throws Exception {
        slackAppender.doAppend(errorEvent("물품 마감 실패: itemId=42", new IllegalStateException("커넥션 없음")));

        assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0))
                .startsWith("{\"text\":\"")
                .contains("물품 마감 실패: itemId=42")
                .contains("IllegalStateException: 커넥션 없음");
    }

    @Test
    @DisplayName("줄바꿈과 따옴표가 섞여도 웹훅이 받는 본문은 깨지지 않는다")
    void escapesJsonPayload() throws Exception {
        slackAppender.doAppend(errorEvent(
                "따옴표 \" 와 줄바꿈\n이 섞인 메시지", new IllegalStateException("역슬래시 \\ 포함")));

        assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();

        String payload = received.get(0);
        assertThat(payload).doesNotContain("\n");
        assertThat(payload).endsWith("\"}");
    }

    @Test
    @DisplayName("물품만 다른 같은 실패가 쿨다운 안에 몰려도 한 번만 보낸다")
    void suppressesRepeatedFailureWithinCooldown() throws Exception {
        IllegalStateException same = new IllegalStateException("커넥션 없음");

        // 실제 호출부가 log.error("...itemId={}", id, e) 형태라, 쿨다운 키가 보는 것은
        // 값이 박히기 전의 포맷 문자열이다. 물품 100개가 한꺼번에 실패해도 한 줄만 나가야 한다.
        slackAppender.doAppend(formattedErrorEvent("물품 마감 실패: itemId={}", 1L, same));
        slackAppender.doAppend(formattedErrorEvent("물품 마감 실패: itemId={}", 2L, same));
        slackAppender.doAppend(formattedErrorEvent("물품 마감 실패: itemId={}", 3L, same));

        assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
        // 뒤의 둘이 늦게 도착할 수 있으니 잠깐 기다렸다가 센다.
        Thread.sleep(300);

        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("웹훅 주소가 비어 있으면 아예 켜지지 않는다")
    void staysStoppedWithoutWebhookUrl() {
        SlackAppender withoutUrl = new SlackAppender();
        withoutUrl.setContext(loggerContext);

        withoutUrl.start();

        assertThat(withoutUrl.isStarted()).isFalse();
    }

    private ILoggingEvent errorEvent(String message, Throwable throwable) {
        return new LoggingEvent(
                SlackAppenderSendTest.class.getName(),
                loggerContext.getLogger(POLLER),
                Level.ERROR,
                message,
                throwable,
                new Object[] {});
    }

    private ILoggingEvent formattedErrorEvent(String format, Object argument, Throwable throwable) {
        return new LoggingEvent(
                SlackAppenderSendTest.class.getName(),
                loggerContext.getLogger(POLLER),
                Level.ERROR,
                format,
                throwable,
                new Object[] {argument});
    }
}
