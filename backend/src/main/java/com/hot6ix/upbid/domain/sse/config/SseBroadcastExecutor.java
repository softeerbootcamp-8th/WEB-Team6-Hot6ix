package com.hot6ix.upbid.domain.sse.config;

import java.util.concurrent.Executor;

/**
 * SSE 브로드캐스트 전송을 넘길 곳. {@link Executor} 를 감싸기만 한다.
 *
 * <p><b>일부러 {@code Executor} 를 구현하지 않는다.</b> 세 가지가 한꺼번에 걸리기 때문이다.
 *
 * <ol>
 *   <li><b>Spring Boot 의 {@code applicationTaskExecutor} 를 밀어낸다.</b> 그 자동 설정은
 *       {@code @ConditionalOnMissingBean(Executor.class)} 라, Executor 타입 빈을 하나라도 만들면
 *       빠진다. 그러면 Spring MVC 비동기 처리가 {@code SimpleAsyncTaskExecutor}(요청마다 새 스레드)로
 *       바뀌어, 브로드캐스트 비동기화 효과를 재려는데 조건이 하나 더 바뀐다.</li>
 *   <li><b>주입 지점이 모호해진다.</b> {@code taskScheduler} 도 Executor 라 후보가 둘이 된다.</li>
 *   <li><b>{@code @Qualifier} 로 풀려다 Lombok 에 막힌다.</b> {@code @RequiredArgsConstructor} 는
 *       {@code lombok.config} 의 {@code copyableAnnotations} 설정 없이는 필드의 {@code @Qualifier} 를
 *       생성자 파라미터로 옮기지 않는다. 실제로 이걸로 앱이 기동에 실패했다.</li>
 * </ol>
 *
 * <p>타입을 따로 두면 셋 다 사라진다 — 자동 설정이 그대로 살고, 타입이 유일해 한정자가 필요 없다.
 */
public final class SseBroadcastExecutor {

    private final Executor delegate;

    public SseBroadcastExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    public void execute(Runnable task) {
        delegate.execute(task);
    }
}
