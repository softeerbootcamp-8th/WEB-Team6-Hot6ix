package com.hot6ix.upbid.domain.sse.dto;

import java.time.Instant;

/**
 * 경매방 최근 이벤트 조회 응답에 담기는 이벤트 하나.
 *
 * <p>SSE로 나갈 때와 같은 구성({@code eventName} = {@code EventType} 이름,
 * {@code data} = 해당 이벤트 DTO)이라, 프론트는 SSE 이벤트 처리와 같은 매핑으로
 * 이 응답을 화면에 반영할 수 있다.
 *
 * @param id         방별 순차 ID
 * @param eventName  SSE 이벤트 이름 ({@code EventType} 이름)
 * @param data       클라이언트로 보낼 DTO
 * @param occurredAt 이벤트가 버퍼에 쌓인 시각
 */
public record RecentRoomEventDto(
        Long id,
        String eventName,
        Object data,
        Instant occurredAt
) {
}
