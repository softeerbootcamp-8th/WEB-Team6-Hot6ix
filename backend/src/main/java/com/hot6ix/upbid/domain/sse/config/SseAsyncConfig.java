package com.hot6ix.upbid.domain.sse.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSE 브로드캐스트 전용 실행기(#234). 톰캣 워커 풀, 마감 스케줄러 풀(#198)과 분리해서,
 * 브로드캐스트가 느려져도 그 두 풀은 영향받지 않게 한다.
 *
 * <p>가상 스레드라 풀 크기·큐가 없다 — emitter마다 새 가상 스레드를 그냥 만들고, 그래서
 * 거부(rejection)도 없다.
 */
@Configuration
public class SseAsyncConfig {

    @Bean
    public Executor sseExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
