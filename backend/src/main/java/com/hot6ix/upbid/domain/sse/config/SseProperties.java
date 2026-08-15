package com.hot6ix.upbid.domain.sse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 연결 유지에 쓰는 값. 지금까지 상수로 박혀 있던 것을 밖으로 뺐다.
 *
 * <p>부하 측정에서 이 둘이 대조 실험의 변수다. heartbeat 주기를 바꿔 마감 지연이 따라
 * 움직이는지 보고, 접속당 힙을 잴 때는 측정 도중에 연결이 만료되지 않도록 타임아웃을 늘린다.
 * 재빌드 없이 바꿀 수 있어야 해서 값으로 열었다.
 */
@ConfigurationProperties(prefix = "upbid.sse")
public record SseProperties(
        long heartbeatIntervalMs,
        long emitterTimeoutMs,
        int bufferSize,
        /**
         * emitter 별 이벤트 큐의 최대 크기. 포화 시 이벤트를 drop하고 해당 emitter를 종료한다.
         * 포화는 느린 구독자의 신호다.
         */
        int emitterQueueCapacity,
        /**
         * SSE emitter drain 스레드 풀 크기. {@code use-virtual-threads=false} 일 때만 쓴다.
         * 기본값 4 는 t4g.micro(2 vCPU) 기준 코어 수 × 2 다.
         */
        int dispatchPoolSize,
        /**
         * {@code true}(기본): emitter 별 bounded 큐를 통해 비동기 전송. 순서 보장, 포화 시 연결 종료.
         * {@code false}: 큐 없이 no-queue executor 에 직접 제출.
         * 배포 환경에서 큐 유무 비교 측정 시 사용한다.
         */
        boolean useQueue,
        /**
         * {@code useQueue=false} + FixedPool 조합에서 no-queue executor 내부 큐 최대 크기.
         * 포화 시 해당 이벤트를 drop 한다. VT 사용 시에는 태스크마다 VT 를 새로 만드므로 무시된다.
         *
         * <p>queue 모드의 executor 는 emitter 별로 drain 태스크가 최대 1개라 자연스럽게
         * emitter 수에 바인딩된다. no-queue 모드도 같은 수준의 상한을 주려면
         * {@code 예상 최대 emitter 수 × 이벤트 burst 여유} 로 잡는다. 기본값 1024 는
         * emitter 200 × burst 5 기준이다.
         */
        int noQueueExecutorCapacity
) {
}
