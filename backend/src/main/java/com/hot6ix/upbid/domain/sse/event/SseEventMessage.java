package com.hot6ix.upbid.domain.sse.event;

import java.time.Instant;

/**
 * Redis 채널에 실려 서버 인스턴스 사이를 오가는 SSE 이벤트 한 건의 <b>본문</b>.
 *
 * <p><b>여기에 ID 는 없다.</b> ID 는 발행 스크립트 안에서 {@code INCR} 로 정해지므로 Java 가
 * 본문을 직렬화하는 시점에는 아직 없다. Lua 에서 JSON 을 조립하게 만드는 대신, 정해진 ID 를
 * 본문 앞에 붙이는 형식으로 나눴다. {@link SseEventEnvelope} 참고.
 *
 * @param roomId     이벤트가 속한 room의 id
 * @param eventName  SSE 이벤트 이름 ({@code EventType} 이름)
 * @param data       클라이언트로 보낼 DTO
 * @param occurredAt 발행 시각. 인스턴스마다 다시 재지 않고 발행자가 찍은 값을 그대로 쓴다.
 */
public record SseEventMessage(
        Long roomId,
        String eventName,
        Object data,
        Instant occurredAt
) {
}
