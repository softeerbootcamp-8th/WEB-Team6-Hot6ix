package com.hot6ix.upbid.domain.sse.event;

/**
 * SSE 가 쓰는 Redis 키 이름. 발행(스크립트)과 조회(버퍼)가 같은 키를 만지므로 한곳에 둔다.
 *
 * <p>{@code {room:<id>}} 로 해시 태그를 걸어 둔 이유는 발행 스크립트가 두 키를 함께 만지기
 * 때문이다. Redis Cluster 는 스크립트가 건드리는 키가 모두 같은 슬롯에 있어야 한다.
 */
public final class SseRedisKeys {

    private static final String PREFIX = "upbid:sse:{room:";

    private SseRedisKeys() {
    }

    /** 방별 순차 ID 카운터. */
    public static String sequence(Long roomId) {
        return PREFIX + roomId + "}:seq";
    }

    /** 재연결 replay 용 이벤트 버퍼 (ZSET, score = 이벤트 ID). */
    public static String events(Long roomId) {
        return PREFIX + roomId + "}:events";
    }
}
