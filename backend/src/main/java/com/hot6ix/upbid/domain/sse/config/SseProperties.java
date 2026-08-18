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
         * 방 종료 시 큐에 남은 이벤트(특히 {@code ROOM_CLOSED})를 전송할 유예 시간. 이 시간이
         * 지나도 전송이 끝나지 않으면 연결을 강제로 닫는다. 클라이언트 소켓이 멈춰 있으면
         * {@code send}가 반환하지 않아, 이 상한이 없으면 emitter 타임아웃까지 남는다(#307).
         */
        long closeFlushTimeoutMs
) {
}
