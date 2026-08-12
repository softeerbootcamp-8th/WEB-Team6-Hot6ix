package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ID 발급은 이 클래스의 일이 아니다. 발행 시점에 Redis {@code INCR}로 정해진 값을 받아 적을
 * 뿐이라, 여기서는 <b>받은 ID대로 쌓고 자르고 잘라 읽는지</b>만 본다.
 */
class SseEventBufferTest {

    private static final Long ROOM_A = 1L;
    private static final Long ROOM_B = 2L;
    private static final int BUFFER_SIZE = 3;

    private SseEventBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new SseEventBuffer(new SseProperties(0L, 0L, BUFFER_SIZE));
    }

    private void add(Long roomId, long id) {
        buffer.add(roomId, id, "EVENT", "data", Instant.now());
    }

    @Test
    @DisplayName("add()는 발행 시점에 정해진 ID를 그대로 저장한다")
    void add_keepsGivenId() {
        buffer.add(ROOM_A, 42L, "BID_PLACED", "data", Instant.now());

        List<BufferedEvent> result = buffer.getAllEvents(ROOM_A);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(42);
    }

    @Test
    @DisplayName("add()는 발행 시각을 다시 재지 않고 넘겨받은 값을 그대로 저장한다")
    void add_keepsGivenOccurredAt() {
        Instant occurredAt = Instant.parse("2026-08-12T03:04:05Z");

        buffer.add(ROOM_A, 1L, "BID_PLACED", "data", occurredAt);

        assertThat(buffer.getAllEvents(ROOM_A).getFirst().occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("N개 초과 시 가장 오래된 이벤트가 밀려난다")
    void add_evictsOldestWhenOverBufferSize() {
        add(ROOM_A, 1);
        add(ROOM_A, 2);
        add(ROOM_A, 3);
        add(ROOM_A, 4);  // id=1이 밀려남

        List<BufferedEvent> result = buffer.getEventsAfter(ROOM_A, 0);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(2);
        assertThat(result.get(2).id()).isEqualTo(4);
    }

    @Test
    @DisplayName("getEventsAfter()는 lastEventId 이후 이벤트만 반환한다")
    void getEventsAfter_returnsOnlyEventsAfterLastId() {
        add(ROOM_A, 1);
        add(ROOM_A, 2);
        add(ROOM_A, 3);

        List<BufferedEvent> result = buffer.getEventsAfter(ROOM_A, 1);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(2);
        assertThat(result.get(1).id()).isEqualTo(3);
    }

    @Test
    @DisplayName("lastEventId가 버퍼에서 밀려난 경우 버퍼 전체를 반환한다")
    void getEventsAfter_returnAllWhenLastIdEvicted() {
        add(ROOM_A, 1);  // 나중에 밀려남
        add(ROOM_A, 2);  // 나중에 밀려남
        add(ROOM_A, 3);
        add(ROOM_A, 4);  // id=1 밀려남
        add(ROOM_A, 5);  // id=2 밀려남

        // id=1은 이미 버퍼에 없음 → 가능한 이벤트를 최대한 복구
        List<BufferedEvent> result = buffer.getEventsAfter(ROOM_A, 1);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(3);
    }

    @Test
    @DisplayName("lastEventId가 최신 id와 같으면 빈 리스트를 반환한다")
    void getEventsAfter_returnsEmptyWhenUpToDate() {
        add(ROOM_A, 1);
        add(ROOM_A, 2);

        List<BufferedEvent> result = buffer.getEventsAfter(ROOM_A, 2);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("버퍼가 없는 방은 빈 리스트를 반환한다")
    void getEventsAfter_returnsEmptyForUnknownRoom() {
        List<BufferedEvent> result = buffer.getEventsAfter(ROOM_A, 0);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAllEvents()는 버퍼가 없는 방에 빈 리스트를 반환한다")
    void getAllEvents_returnsEmptyForUnknownRoom() {
        List<BufferedEvent> result = buffer.getAllEvents(ROOM_A);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAllEvents()는 버퍼에 남은 이벤트를 오래된 것부터 전부 반환한다")
    void getAllEvents_returnsAllInAscendingOrder() {
        add(ROOM_A, 1);
        add(ROOM_A, 2);

        List<BufferedEvent> result = buffer.getAllEvents(ROOM_A);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1);
        assertThat(result.get(1).id()).isEqualTo(2);
    }

    @Test
    @DisplayName("getAllEvents()는 N개 초과로 밀려난 이벤트는 제외한다")
    void getAllEvents_excludesEvictedEvents() {
        add(ROOM_A, 1);
        add(ROOM_A, 2);
        add(ROOM_A, 3);
        add(ROOM_A, 4);  // id=1이 밀려남

        List<BufferedEvent> result = buffer.getAllEvents(ROOM_A);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(2);
        assertThat(result.get(2).id()).isEqualTo(4);
    }

    @Test
    @DisplayName("clear() 후에는 빈 리스트를 반환한다")
    void clear_removesBuffer() {
        add(ROOM_A, 1);
        add(ROOM_A, 2);

        buffer.clear(ROOM_A);

        assertThat(buffer.getEventsAfter(ROOM_A, 0)).isEmpty();
    }

    @Test
    @DisplayName("clear()는 해당 방만 삭제하고 다른 방에 영향을 주지 않는다")
    void clear_doesNotAffectOtherRooms() {
        add(ROOM_A, 1);
        add(ROOM_B, 1);

        buffer.clear(ROOM_A);

        assertThat(buffer.getEventsAfter(ROOM_A, 0)).isEmpty();
        assertThat(buffer.getEventsAfter(ROOM_B, 0)).hasSize(1);
    }
}
