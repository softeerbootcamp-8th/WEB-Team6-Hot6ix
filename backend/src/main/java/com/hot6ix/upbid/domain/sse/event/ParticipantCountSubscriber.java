package com.hot6ix.upbid.domain.sse.event;

import com.hot6ix.upbid.domain.sse.service.RoomSseManager;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis 채널에서 참여자 수 전역 합계를 받아 이 인스턴스에 있는 커넥션으로 보낸다.
 *
 * <p>{@code SseEventSubscriber}와 달리 버퍼에는 아무것도 안 쌓는다 — 발행 스크립트가
 * 애초에 버퍼를 안 쓴다. 메시지 형식도 {@code id\njson}이 아니라 {@code roomId:count} 다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParticipantCountSubscriber implements MessageListener {

    private final RoomSseManager roomSseManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String raw = new String(message.getBody(), StandardCharsets.UTF_8);
            int separator = raw.indexOf(':');

            if (separator < 0) {
                throw new IllegalArgumentException("참여자 수 이벤트 형식이 아니다: " + raw);
            }

            Long roomId = Long.parseLong(raw.substring(0, separator));
            int count = Integer.parseInt(raw.substring(separator + 1));

            roomSseManager.deliverParticipantCountLocal(roomId, count);

        } catch (RuntimeException e) {
            log.error("참여자 수 이벤트 수신 처리 실패", e);
        }
    }
}
