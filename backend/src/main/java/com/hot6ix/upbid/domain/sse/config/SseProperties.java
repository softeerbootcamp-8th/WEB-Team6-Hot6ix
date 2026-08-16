package com.hot6ix.upbid.domain.sse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 연결 유지에 쓰는 값. 지금까지 상수로 박혀 있던 것을 밖으로 뺐다.
 *
 * <p>heartbeat 주기를 바꿔 마감 지연이 따라 움직이는지 보고, 접속당 힙을 잴 때는
 * 측정 도중에 연결이 만료되지 않도록 타임아웃을 늘린다.
 * 재빌드 없이 바꿀 수 있어야 해서 값으로 열었다.
 */
@ConfigurationProperties(prefix = "upbid.sse")
public record SseProperties(
        long heartbeatIntervalMs,
        long emitterTimeoutMs,
        int bufferSize,
        /** 참여자 수 전역 집계에서 이 인스턴스를 구분하는 값(#311). */
        String serverHostname,
        /**
         * emitter 별 이벤트 큐의 최대 크기. 포화 시 이벤트를 drop하고 해당 emitter를 종료한다.
         * 포화는 느린 구독자의 신호다.
         */
        int emitterQueueCapacity,
        /**
         * emitter drain 전용 고정 스레드 풀의 스레드 수.
         * VT 대신 플랫폼 스레드를 쓸 때 이 값으로 풀 크기를 결정한다.
         */
        int workerPoolSize,
        /**
         * sseWorkerExecutor 의 내부 대기 큐 크기.
         * 풀 스레드가 모두 바쁠 때 drain 태스크가 여기서 대기한다.
         * 최악의 경우 대기 수 ≈ 활성 emitter 수 - 풀 크기이므로,
         * 이 값이 곧 고정 풀이 수용 가능한 동시 emitter 상한이 된다.
         * 초과 시 RejectedExecutionException → VT 와 FixedPool의 한계가 갈리는 지점.
         */
        int workerQueueCapacity
) {
}
