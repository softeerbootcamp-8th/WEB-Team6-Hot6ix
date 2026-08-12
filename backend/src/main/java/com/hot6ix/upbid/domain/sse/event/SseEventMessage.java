package com.hot6ix.upbid.domain.sse.event;

import java.time.Instant;

/**
 * Redis 채널에 실려 서버 인스턴스 사이를 오가는 SSE 이벤트 한 건.
 *
 * @param roomId     이벤트가 속한 room의 id
 * @param id         방별 순차 id. 브라우저가 Last-Event-ID로 해당 값을 돌려준다.
 * @param eventName  SSE 이벤트 이름 ({@code EventType} 이름)
 * @param data       클라이언트로 보낼 DTO
 * @param occurredAt 발행 시각. 인스턴스마다 다시 재지 않고 발행자가 찍은 값을 그대로 쓴다.
 */
public record SseEventMessage(
        Long roomId,
        long id,
        String eventName,
        Object data,
        Instant occurredAt
) {
}
