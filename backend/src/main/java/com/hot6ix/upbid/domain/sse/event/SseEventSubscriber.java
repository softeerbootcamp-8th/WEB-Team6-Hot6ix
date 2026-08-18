package com.hot6ix.upbid.domain.sse.event;

import com.hot6ix.upbid.domain.sse.service.RoomSseManager;
import com.hot6ix.upbid.global.event.EventType;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis 채널에서 SSE 이벤트를 받아 해당 인스턴스에 있는 커넥션으로 보낸다.
 *
 * <p><b>버퍼에는 쓰지 않는다.</b> 발행 스크립트가 이미 넣었다. 여기서 또 넣으면 인스턴스 수만큼
 * 같은 값을 덮어쓰게 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventSubscriber implements MessageListener, SubscriptionListener {

    private final ObjectMapper objectMapper;
    private final RoomSseManager roomSseManager;

    /** 최초 구독(앱 기동)과 재구독(재연결)을 구분하기 위한 상태(#378). */
    private final AtomicBoolean subscribed = new AtomicBoolean(false);

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            deliver(SseEventEnvelope.decode(
                    new String(message.getBody(), StandardCharsets.UTF_8), objectMapper));

        } catch (RuntimeException e) {
            log.error("sse 이벤트 수신 처리 실패", e);
        }
    }

    private void deliver(SseEventEnvelope envelope) {
        SseEventMessage event = envelope.message();

        roomSseManager.deliverLocal(event.roomId(), event.eventName(), envelope.id(), event.data());

        if (EventType.ROOM_CLOSED.name().equals(event.eventName())) {
            roomSseManager.closeRoom(event.roomId());
        }
    }

    /**
     * 이 채널(SSE 이벤트 채널) 구독이 (재)수립될 때마다 불린다(#378) — 앱 기동 시 최초 구독과,
     * Redis 연결이 끊겼다가 Lettuce가 자동으로 재구독할 때 둘 다 포함한다. 최초 구독은
     * 재연결이 아니므로 건너뛰고, 그 이후에 불리는 것만 "재연결"로 취급한다.
     *
     * <p>{@code RedisMessageListenerContainer}엔 이 콜백을 등록하는 별도 API가 없다. 등록된
     * {@code MessageListener}가 {@code SubscriptionListener}도 같이 구현하면, 컨테이너가
     * {@code instanceof}로 감지해서 자동으로 이 메서드를 호출해준다.
     *
     * <p>이 앱은 SSE pub/sub·캐시·ShedLock이 단일 Lettuce 클라이언트를 공유하지만, 이 콜백은
     * 정확히 이 채널의 구독 상태 변화에만 반응하므로 다른 용도의 연결과 섞이지 않는다.
     */
    @Override
    public void onChannelSubscribed(byte[] channel, long count) {
        if (subscribed.getAndSet(true)) {
            log.info("sse pub/sub 재연결 감지: channel={}", new String(channel, StandardCharsets.UTF_8));
            roomSseManager.replayAfterReconnect();
        }
    }
}
