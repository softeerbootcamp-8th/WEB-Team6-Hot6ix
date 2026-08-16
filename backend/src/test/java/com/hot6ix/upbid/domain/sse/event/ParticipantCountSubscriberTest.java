package com.hot6ix.upbid.domain.sse.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.sse.service.RoomSseManager;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

/**
 * 채널에서 받은 참여자 수 메시지를 이 인스턴스가 어떻게 처리하는지 본다. Redis 자체는
 * 관심사가 아니라 {@link Message}를 직접 만들어 넣는다. 실제 왕복은
 * {@code ParticipantCountPublisherTest}가 본다.
 */
class ParticipantCountSubscriberTest {

    private static final Long ROOM_ID = 10L;

    private final RoomSseManager roomSseManager = mock(RoomSseManager.class);
    private final ParticipantCountSubscriber subscriber = new ParticipantCountSubscriber(roomSseManager);

    @Test
    @DisplayName("roomId:합계 메시지를 받으면 그 방의 로컬 연결로 전달한다")
    void deliversParsedRoomIdAndCount() {

        subscriber.onMessage(rawMessage("10:3"), null);

        verify(roomSseManager).deliverParticipantCountLocal(ROOM_ID, 3);
    }

    @Test
    @DisplayName("콜론이 없는 메시지는 구독 스레드를 흔들지 않는다")
    void survivesMessageWithoutSeparator() {

        assertThatCode(() -> subscriber.onMessage(rawMessage("no-separator"), null))
                .doesNotThrowAnyException();

        verify(roomSseManager, never()).deliverParticipantCountLocal(any(), anyInt());
    }

    @Test
    @DisplayName("roomId 자리가 숫자가 아닌 메시지도 구독 스레드를 흔들지 않는다")
    void survivesMessageWithNonNumericRoomId() {

        assertThatCode(() -> subscriber.onMessage(rawMessage("abc:3"), null))
                .doesNotThrowAnyException();

        verify(roomSseManager, never()).deliverParticipantCountLocal(any(), anyInt());
    }

    @Test
    @DisplayName("합계 자리가 숫자가 아닌 메시지도 구독 스레드를 흔들지 않는다")
    void survivesMessageWithNonNumericCount() {

        assertThatCode(() -> subscriber.onMessage(rawMessage("10:abc"), null))
                .doesNotThrowAnyException();

        verify(roomSseManager, never()).deliverParticipantCountLocal(any(), anyInt());
    }

    private Message rawMessage(String body) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
