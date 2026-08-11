package com.hot6ix.upbid.domain.sse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * SSE 브로드캐스트 전용 스레드풀(#234). 톰캣 워커 풀, 마감 스케줄러 풀(#198)과 분리해서,
 * 브로드캐스트가 느려져도 그 두 풀은 영향받지 않게 한다.
 */
@Configuration
public class SseAsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor sseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("SSE_Async-");
        executor.initialize();
        return executor;
    }
}
