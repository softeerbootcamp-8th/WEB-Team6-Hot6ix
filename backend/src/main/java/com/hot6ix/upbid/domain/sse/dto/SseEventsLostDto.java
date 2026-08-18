package com.hot6ix.upbid.domain.sse.dto;

/**
 * 재연결 replay 로 메울 수 없는 구간이 있었음을 알리는 payload.
 *
 * <p>화면은 이 값을 받으면 상태를 다시 조회한다. <b>어떤 이벤트가 사라졌는지는 서버도 모른다</b>
 * — 버퍼에서 밀려난 이벤트는 이름도 남지 않아서 골라내는 것 자체가 불가능하다. 그래서
 * 개수나 목록 대신 사유만 싣는다.
 *
 * @param reason 유실을 판정한 근거. 화면 동작은 값과 무관하고, 운영에서 어느 경로로 유실이
 *               나는지 구분하는 데 쓴다.
 */
public record SseEventsLostDto(String reason) {

    /** 끊긴 사이 이벤트가 버퍼 크기를 넘겨 오래된 것부터 밀려났다. */
    public static final String BUFFER_OVERFLOW = "BUFFER_OVERFLOW";

    /** 버퍼가 통째로 비어 있다. Redis 재시작·failover·TTL 만료 등. */
    public static final String BUFFER_MISSING = "BUFFER_MISSING";

    /** 클라이언트가 든 ID가 버퍼의 마지막 ID보다 크다. 순차 ID 카운터가 되감겼다. */
    public static final String SEQUENCE_RESET = "SEQUENCE_RESET";
}
