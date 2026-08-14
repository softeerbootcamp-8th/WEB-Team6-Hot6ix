package com.hot6ix.upbid.domain.sse.event;

import tools.jackson.databind.ObjectMapper;

/**
 * 채널과 버퍼에 저장되는 문자열의 형식.
 *
 * <pre>
 * 12\n{"roomId":10,"eventName":"BID_PLACED","data":{...},"occurredAt":"..."}
 * └┬┘ └────────────────────────────┬───────────────────────────────────────┘
 *  ID                            본문 JSON
 * </pre>
 *
 * <p>ID 를 JSON 안에 넣지 않은 이유는 ID 가 publish lua script 안에서 정해지기 때문이다.
 * 만약 JSON 안에 id 가 있다면 lua script가 직접 json으로 조립해야 하는데
 * 그러면 본문 구조가 바뀔 때마다 스크립트도 함께 변경해야 한다.
 */
public record SseEventEnvelope(long id, SseEventMessage message) {

    private static final char SEPARATOR = '\n';

    /**
     * 운영에서 이 형식을 실제로 만드는 것은 {@code redis/sse-publish.lua} 다. ID 를 스크립트가
     * 정하기 때문이다. 이 메서드는 형식을 Java 쪽에서도 한 번 적어 두어 테스트가 같은 문자열을
     * 만들 수 있게 한다.
     */
    public static String encode(long id, String body) {
        return id + String.valueOf(SEPARATOR) + body;
    }

    public static SseEventEnvelope decode(String raw, ObjectMapper objectMapper) {
        int separator = raw.indexOf(SEPARATOR);

        if (separator < 0) {
            throw new IllegalArgumentException("sse 이벤트 형식이 아니다: " + raw);
        }

        long id = Long.parseLong(raw.substring(0, separator));

        return new SseEventEnvelope(
                id, objectMapper.readValue(raw.substring(separator + 1), SseEventMessage.class));
    }
}
