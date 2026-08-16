package com.hot6ix.upbid.global.alert;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ERROR 로그를 Slack 웹훅으로 보내는 logback appender.
 *
 * <p>알림 경로를 로그 하나로 통일하려고 둔다. 서비스 코드가 알림을 직접 부르면 스케줄러와
 * 이벤트 리스너처럼 요청 밖에서 도는 자리를 계속 빠뜨리게 된다. {@code log.error} 를 남기는
 * 것만으로 알림이 나가면 앞으로 생기는 실패 지점도 자동으로 덮인다.
 *
 * <p>설정은 {@code logback-spring.xml} 이 {@code <springProperty>} 로 넣어 준다. appender 는
 * Spring 빈이 아니라 주입을 받을 수 없어서, 값을 setter 로 받는 이 방법이 표준이다.
 */
public class SlackAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final int STACK_FRAME_LIMIT = 6;
    private static final int CAUSE_DEPTH_LIMIT = 3;
    private static final int MESSAGE_LIMIT = 2500;
    private static final String OUR_PACKAGE = "com.hot6ix.upbid";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SlackRateLimiter rateLimiter = new SlackRateLimiter();

    private String webhookUrl;
    private long cooldownSeconds = 60L;
    private int queueSize = 256;

    private HttpClient httpClient;
    private ThreadPoolExecutor sender;

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public void setCooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    @Override
    public void start() {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            addWarn("app.alert.slack.webhook-url 이 비어 있어 Slack 알림을 켜지 않는다.");
            return;
        }

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // 큐가 차면 CallerRunsPolicy 가 아니라 버린다. 부하 중에 ERROR 가 몰리면
        // CallerRuns 는 요청 스레드가 웹훅 응답을 기다리게 만들고, 앱이 느려진 원인이
        // 알림 전송이 되는 상황이 된다. 알림은 놓쳐도 로그에는 그대로 남는다.
        sender = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize),
                runnable -> {
                    Thread thread = new Thread(runnable, "slack-alert");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy());

        super.start();
    }

    @Override
    public void stop() {
        if (sender != null) {
            sender.shutdownNow();
        }
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        // 자기 자신이 남긴 로그를 다시 집으면 전송 실패가 전송 실패를 부르는 고리가 된다.
        if (event.getLoggerName().startsWith(SlackAppender.class.getName())) {
            return;
        }

        if (!rateLimiter.shouldSend(errorKey(event), cooldownSeconds)) {
            return;
        }

        String payload = toPayload(buildMessage(event));
        sender.execute(() -> post(payload));
    }

    /**
     * 쿨다운을 걸 단위를 만든다.
     *
     * <p>도메인 키(itemId 등)를 일부러 넣지 않는다. 넣으면 물품 100개가 한꺼번에 실패할 때
     * 채널에 100줄이 쌓여서, 도배를 막으려고 둔 쿨다운이 무력해진다. 어느 물품이었는지는
     * 본문의 로그 메시지에 그대로 남는다.
     */
    String errorKey(ILoggingEvent event) {
        IThrowableProxy throwable = event.getThrowableProxy();

        return event.getLoggerName()
                + " " + event.getMessage()
                + " " + (throwable == null ? "no-exception" : throwable.getClassName())
                + " " + originOf(throwable);
    }

    private String originOf(IThrowableProxy throwable) {
        if (throwable == null) {
            return "unknown";
        }

        return ourFrames(throwable).stream()
                .findFirst()
                .orElse("unknown");
    }

    /**
     * 우리 패키지 프레임을 우선해서 고른다.
     *
     * <p>스택을 앞에서 그냥 자르면 프록시와 프레임워크 프레임이 자리를 다 먹어서, 정작
     * 어느 코드가 터뜨렸는지가 안 보인다.
     */
    private List<String> ourFrames(IThrowableProxy throwable) {
        List<String> ours = new ArrayList<>();
        List<String> others = new ArrayList<>();

        for (StackTraceElementProxy proxy : throwable.getStackTraceElementProxyArray()) {
            String frame = proxy.getStackTraceElement().toString();
            if (frame.startsWith(OUR_PACKAGE)) {
                ours.add(frame);
            } else if (others.size() < STACK_FRAME_LIMIT) {
                others.add(frame);
            }
            if (ours.size() >= STACK_FRAME_LIMIT) {
                break;
            }
        }

        return ours.isEmpty() ? others : ours;
    }

    String buildMessage(ILoggingEvent event) {
        StringBuilder body = new StringBuilder()
                .append("🚨 *[").append(event.getLevel()).append("]* `")
                .append(shortLoggerName(event.getLoggerName())).append("`\n")
                .append("*시각*: ").append(FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp()))).append('\n')
                .append("*메시지*: ").append(event.getFormattedMessage()).append('\n');

        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            body.append("*예외*: `").append(causeChain(throwable)).append("`\n")
                    .append("*스택트레이스*:\n```")
                    .append(String.join("\n  at ", ourFrames(throwable)))
                    .append("```");
        }

        String message = body.toString();
        return message.length() > MESSAGE_LIMIT ? message.substring(0, MESSAGE_LIMIT) + "…(생략)" : message;
    }

    /**
     * 원인 예외를 끝까지 따라가서 한 줄로 잇는다.
     *
     * <p>맨 바깥 예외만 찍으면 {@code DataIntegrityViolationException} 처럼 껍데기만 남고
     * 실제 원인이 안 보인다.
     */
    private String causeChain(IThrowableProxy throwable) {
        StringBuilder chain = new StringBuilder();

        IThrowableProxy current = throwable;
        for (int depth = 0; current != null && depth < CAUSE_DEPTH_LIMIT; depth++) {
            if (depth > 0) {
                chain.append(" ← ");
            }
            chain.append(current.getClassName());
            if (current.getMessage() != null) {
                chain.append(": ").append(current.getMessage());
            }
            current = current.getCause();
        }

        return chain.toString();
    }

    private String shortLoggerName(String loggerName) {
        int lastDot = loggerName.lastIndexOf('.');
        return lastDot < 0 ? loggerName : loggerName.substring(lastDot + 1);
    }

    private String toPayload(String message) {
        return "{\"text\":\"" + message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", " ")
                + "\"}";
    }

    private void post(String payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // logback 내부 status 로만 남긴다. 여기서 log.error 를 부르면 이 appender 가
            // 자기 실패를 다시 집어서 전송을 무한히 되풀이한다.
            addWarn("Slack 알림 전송 실패: " + e.getMessage());
        }
    }
}
