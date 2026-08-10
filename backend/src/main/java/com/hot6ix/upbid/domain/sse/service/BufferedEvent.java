package com.hot6ix.upbid.domain.sse.service;

import java.time.Instant;

/**
 * SSE 버퍼에 저장되는 이벤트 단위.
 *
 * @param id         방별 순차 ID. 클라이언트에게 {@code Last-Event-ID}로 전달된다.
 * @param eventName  SSE 이벤트 이름 ({@code EventType} 이름)
 * @param data       클라이언트로 보낼 DTO
 * @param occurredAt 버퍼에 쌓인 시각. 재연결 replay(SSE)에는 안 쓰이고, 최근 이벤트
 *                   조회(GET) 응답에서 화면에 시각을 보여줄 때 쓴다.
 */
public record BufferedEvent(long id, String eventName, Object data, Instant occurredAt) {
}
