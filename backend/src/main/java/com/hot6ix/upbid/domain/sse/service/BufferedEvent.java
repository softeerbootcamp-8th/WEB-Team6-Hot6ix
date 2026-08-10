package com.hot6ix.upbid.domain.sse.service;

/**
 * SSE 버퍼에 저장되는 이벤트 단위.
 *
 * @param id        방별 순차 ID. 클라이언트에게 {@code Last-Event-ID}로 전달된다.
 * @param eventName SSE 이벤트 이름 ({@code EventType} 이름)
 * @param data      클라이언트로 보낼 DTO
 */
public record BufferedEvent(long id, String eventName, Object data) {
}
