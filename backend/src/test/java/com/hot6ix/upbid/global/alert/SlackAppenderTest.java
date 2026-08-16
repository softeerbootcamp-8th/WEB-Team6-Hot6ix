package com.hot6ix.upbid.global.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 알림 본문과 쿨다운 키를 만드는 부분만 검증한다. 웹훅 전송은 여기서 다루지 않는다.
 *
 * <p>이 둘이 이 클래스의 알맹이다. 본문이 부실하면 알림이 와도 원인을 못 짚고, 쿨다운 키가
 * 너무 잘게 갈리면 물품 하나가 실패할 때마다 한 줄씩 나가서 채널이 덮인다.
 */
class SlackAppenderTest {

    private static final String POLLER = "com.hot6ix.upbid.domain.auction.scheduler.AuctionClosePoller";

    private LoggerContext loggerContext;
    private SlackAppender slackAppender;

    @BeforeEach
    void setUp() {
        loggerContext = new LoggerContext();
        slackAppender = new SlackAppender();
    }

    @Test
    @DisplayName("원인 예외를 끝까지 따라가 한 줄로 잇는다")
    void joinsCauseChain() {
        Exception root = new IllegalStateException("커넥션이 없다");
        Exception middle = new RuntimeException("트랜잭션 실패", root);
        Exception outer = new RuntimeException("물품 마감 실패", middle);

        String message = slackAppender.buildMessage(errorEvent("물품 마감 실패", outer));

        assertThat(message)
                .contains("RuntimeException: 물품 마감 실패")
                .contains("RuntimeException: 트랜잭션 실패")
                .contains("IllegalStateException: 커넥션이 없다");
    }

    @Test
    @DisplayName("맨 바깥 예외만 있으면 그것만 적는다")
    void writesSingleExceptionWhenNoCause() {
        String message = slackAppender.buildMessage(
                errorEvent("마감 실패", new IllegalStateException("혼자다")));

        assertThat(message)
                .contains("IllegalStateException: 혼자다")
                .doesNotContain("←");
    }

    @Test
    @DisplayName("스택에 우리 패키지가 있으면 프레임워크 프레임 대신 그쪽을 싣는다")
    void prefersOurOwnStackFrames() {
        Exception e = withStackTrace(
                new IllegalStateException("실패"),
                frame("org.springframework.aop.SomeProxy", "invoke"),
                frame("jdk.internal.reflect.Method", "invoke"),
                frame("com.hot6ix.upbid.domain.auction.service.AuctionItemCloseService", "closeIfDue"));

        String message = slackAppender.buildMessage(errorEvent("마감 실패", e));

        assertThat(message)
                .contains("AuctionItemCloseService.closeIfDue")
                .doesNotContain("org.springframework.aop.SomeProxy");
    }

    @Test
    @DisplayName("우리 패키지 프레임이 하나도 없으면 있는 것이라도 싣는다")
    void fallsBackToForeignFramesWhenNoneAreOurs() {
        Exception e = withStackTrace(
                new IllegalStateException("실패"),
                frame("org.springframework.aop.SomeProxy", "invoke"));

        String message = slackAppender.buildMessage(errorEvent("마감 실패", e));

        assertThat(message).contains("org.springframework.aop.SomeProxy.invoke");
    }

    @Test
    @DisplayName("포맷 인자가 채워진 메시지를 싣는다")
    void writesFormattedMessage() {
        LoggingEvent event = new LoggingEvent(
                SlackAppenderTest.class.getName(),
                loggerContext.getLogger(POLLER),
                Level.ERROR,
                "물품 마감 실패: itemId={}",
                new IllegalStateException("실패"),
                new Object[] {42L});

        assertThat(slackAppender.buildMessage(event)).contains("물품 마감 실패: itemId=42");
    }

    @Test
    @DisplayName("물품만 다른 같은 실패는 쿨다운 키가 같아서 한 줄만 나간다")
    void groupsSameFailureRegardlessOfDomainKey() {
        Exception e = withStackTrace(
                new IllegalStateException("실패"),
                frame("com.hot6ix.upbid.domain.auction.service.AuctionItemCloseService", "closeIfDue"));

        String first = slackAppender.errorKey(formattedEvent("물품 마감 실패: itemId={}", 1L, e));
        String second = slackAppender.errorKey(formattedEvent("물품 마감 실패: itemId={}", 999L, e));

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("다른 자리에서 난 실패는 쿨다운 키가 갈린다")
    void separatesFailuresFromDifferentPlaces() {
        Exception e = new IllegalStateException("실패");

        String close = slackAppender.errorKey(errorEvent("물품 마감 실패", e));
        String award = slackAppender.errorKey(errorEvent("낙찰 후보 생성 실패", e));

        assertThat(close).isNotEqualTo(award);
    }

    private ILoggingEvent errorEvent(String message, Throwable throwable) {
        return new LoggingEvent(
                SlackAppenderTest.class.getName(),
                loggerContext.getLogger(POLLER),
                Level.ERROR,
                message,
                throwable,
                new Object[] {});
    }

    private ILoggingEvent formattedEvent(String format, Object argument, Throwable throwable) {
        return new LoggingEvent(
                SlackAppenderTest.class.getName(),
                loggerContext.getLogger(POLLER),
                Level.ERROR,
                format,
                throwable,
                new Object[] {argument});
    }

    private Exception withStackTrace(Exception e, StackTraceElement... frames) {
        e.setStackTrace(frames);
        return e;
    }

    private StackTraceElement frame(String className, String methodName) {
        return new StackTraceElement(className, methodName, "Source.java", 1);
    }
}
