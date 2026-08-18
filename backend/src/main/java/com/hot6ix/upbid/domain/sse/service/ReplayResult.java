package com.hot6ix.upbid.domain.sse.service;

import java.util.List;

/**
 * 재연결 replay 조회 결과.
 *
 * <p>{@code lostReason}이 있으면 <b>버퍼로 메울 수 없는 구간이 있었다</b>는 뜻이다. 이때
 * {@code events}는 여전히 최선의 결과이고(남은 것은 그대로 돌려준다), 비어 있을 수도 있다.
 *
 * <p>유실 규모를 개수로 싣지 않는 이유는 {@link com.hot6ix.upbid.domain.sse.dto.SseEventsLostDto}
 * 와 같다. 버퍼가 통째로 사라진 경우에는 개수를 구할 방법 자체가 없고, 받는 쪽이 하는 일은
 * 어느 쪽이든 "상태 재조회 한 번"으로 같다. 개수는 로그로만 남긴다.
 *
 * @param events     {@code lastEventId} 이후로 버퍼에 남아 있던 이벤트. ID 오름차순
 * @param lostReason 유실 사유. 유실이 없으면 {@code null}
 */
public record ReplayResult(List<BufferedEvent> events, String lostReason) {

    static ReplayResult intact(List<BufferedEvent> events) {
        return new ReplayResult(events, null);
    }

    static ReplayResult lost(List<BufferedEvent> events, String reason) {
        return new ReplayResult(events, reason);
    }

    public boolean hasLoss() {
        return lostReason != null;
    }
}
