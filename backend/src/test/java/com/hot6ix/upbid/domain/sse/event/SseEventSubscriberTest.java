package com.hot6ix.upbid.domain.sse.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.sse.service.RoomSseManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.json.JsonMapper;

/**
 * 채널에서 받은 이벤트를 이 인스턴스가 어떻게 처리하는지 본다. Redis 자체는 이 테스트의
 * 관심사가 아니라 {@link Message}를 직접 만들어 넣는다. 실제 왕복은
 * {@code SseEventRedisRoundTripTest}가 본다.
 */
class SseEventSubscriberTest {

    private static final Long ROOM_ID = 10L;

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final RoomSseManager roomSseManager = mock(RoomSseManager.class);

    private final SseEventSubscriber subscriber =
            new SseEventSubscriber(objectMapper, roomSseManager);

    @Test
    @DisplayName("받은 이벤트를 발행 쪽이 정한 ID 그대로 이 인스턴스의 연결로 내보낸다")
    void deliversReceivedEventWithPublishedId() {

        subscriber.onMessage(message(7L, new SseEventMessage(
                ROOM_ID, "BID_PLACED", Map.of("bidPrice", 12_000), Instant.now())), null);

        // ID를 인스턴스마다 다시 매기면 재연결 기준점이 어긋난다.
        verify(roomSseManager).deliverLocal(eq(ROOM_ID), eq("BID_PLACED"), eq(7L), any());
    }

    @Test
    @DisplayName("방 종료는 이벤트를 내보낸 뒤에 연결을 끊는다")
    void closesRoomAfterDeliveringRoomClosed() {

        subscriber.onMessage(message(1L, new SseEventMessage(
                ROOM_ID, "ROOM_CLOSED", Map.of("roomTitle", "승민의 경매방"), Instant.now())), null);

        // 순서가 뒤집히면 마지막 이벤트가 도착하기 전에 연결이 끊긴다.
        InOrder inOrder = inOrder(roomSseManager);
        inOrder.verify(roomSseManager).deliverLocal(eq(ROOM_ID), eq("ROOM_CLOSED"), eq(1L), any());
        inOrder.verify(roomSseManager).closeRoom(ROOM_ID);
    }

    @Test
    @DisplayName("방 종료가 아닌 이벤트는 연결을 끊지 않는다")
    void keepsConnectionOnOtherEvents() {

        subscriber.onMessage(message(1L, new SseEventMessage(
                ROOM_ID, "ITEM_ENDED", Map.of("itemId", 30), Instant.now())), null);

        verify(roomSseManager, never()).closeRoom(any());
    }

    @Test
    @DisplayName("깨진 메시지 한 건이 구독 스레드를 흔들지 않는다")
    void survivesUnparseableMessage() {

        assertThatCode(() -> subscriber.onMessage(rawMessage("not an event"), null))
                .doesNotThrowAnyException();

        verify(roomSseManager, never()).deliverLocal(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("ID 자리가 숫자가 아닌 메시지도 구독 스레드를 흔들지 않는다")
    void survivesMessageWithNonNumericId() {

        assertThatCode(() -> subscriber.onMessage(rawMessage("abc\n{\"roomId\":10}"), null))
                .doesNotThrowAnyException();

        verify(roomSseManager, never()).deliverLocal(any(), any(), anyLong(), any());
    }

    private Message message(long id, SseEventMessage event) {
        return rawMessage(SseEventEnvelope.encode(id, objectMapper.writeValueAsString(event)));
    }

    private Message rawMessage(String body) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
