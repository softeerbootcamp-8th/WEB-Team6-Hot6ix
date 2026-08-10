package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SseEventBufferTest {

    private static final Long ROOM_A = 1L;
    private static final Long ROOM_B = 2L;
    private static final int BUFFER_SIZE = 3;

    private SseEventBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new SseEventBuffer(new SseProperties(0L, 0L, BUFFER_SIZE));
    }

    @Test
    @DisplayName("add()는 방별 순차 ID를 1부터 발급한다")
    void add_assignsSequentialIdStartingFromOne() {
        long id1 = buffer.add(ROOM_A, "BID_PLACED", "data");
        long id2 = buffer.add(ROOM_A, "BID_PLACED", "data");
        long id3 = buffer.add(ROOM_A, "BID_PLACED", "data");

        assertThat(id1).isEqualTo(1);
        assertThat(id2).isEqualTo(2);
        assertThat(id3).isEqualTo(3);
    }

    @Test
    @DisplayName("방별 ID는 독립적이다 — 방 A와 방 B의 ID가 따로 간다")
    void add_idIsIndependentPerRoom() {
        long roomAId = buffer.add(ROOM_A, "BID_PLACED", "data");
        long roomBId = buffer.add(ROOM_B, "BID_PLACED", "data");

        assertThat(roomAId).isEqualTo(1);
        assertThat(roomBId).isEqualTo(1);
    }

    @Test
    @DisplayName("N개 초과 시 가장 오래된 이벤트가 밀려난다")
    void add_evictsOldestWhenOverBufferSize() {
        buffer.add(ROOM_A, "EVENT", "id=1");
        buffer.add(ROOM_A, "EVENT", "id=2");
        buffer.add(ROOM_A, "EVENT", "id=3");
        buffer.add(ROOM_A, "EVENT", "id=4");  // id=1이 밀려남

        List<BufferedEvent> result = buffer.getEventsAfter(ROOM_A, 0);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(2);
        assertThat(result.get(2).id()).isEqualTo(4);
    }

    @Test
    @DisplayName("getEventsAfter()는 lastEventId 이후 이벤트만 반환한다")
    void getEventsAfter_returnsOnlyEventsAfterLastId() {
        buffer.add(ROOM_A, "EVENT", "data");  // id=1
        buffer.add(ROOM_A, "EVENT", "data");  // id=2
        buffer.add(ROOM_A, "EVENT", "data");  // id=3

        List<BufferedEvent> result = buffer.getEventsAfter(ROOM_A, 1);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(2);
        assertThat(result.get(1).id()).isEqualTo(3);
    }

    @Test
    @DisplayName("lastEventId가 버퍼에서 밀려난 경우 버퍼 전체를 반환한다")
    void getEventsAfter_returnAllWhenLastIdEvicted() {
        buffer.add(ROOM_A, "EVENT", "data");  // id=1 → 나중에 밀려남
        buffer.add(ROOM_A, "EVENT", "data");  // id=2 → 나중에 밀려남
        buffer.add(ROOM_A, "EVENT", "data");  // id=3
        buffer.add(ROOM_A, "EVENT", "data");  // id=4, id=1 밀려남
        buffer.add(ROOM_A, "EVENT", "data");  // id=5, id=2 밀려남

        // id=1은 이미 버퍼에 없음 → 가능한 이벤트를 최대한 복구
        List<BufferedEvent> result = buffer.getEventsAfter(ROOM_A, 1);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(3);
    }

    @Test
    @DisplayName("lastEventId가 최신 id와 같으면 빈 리스트를 반환한다")
    void getEventsAfter_returnsEmptyWhenUpToDate() {
        buffer.add(ROOM_A, "EVENT", "data");  // id=1
        buffer.add(ROOM_A, "EVENT", "data");  // id=2

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
        buffer.add(ROOM_A, "EVENT", "data");  // id=1
        buffer.add(ROOM_A, "EVENT", "data");  // id=2

        List<BufferedEvent> result = buffer.getAllEvents(ROOM_A);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1);
        assertThat(result.get(1).id()).isEqualTo(2);
    }

    @Test
    @DisplayName("getAllEvents()는 N개 초과로 밀려난 이벤트는 제외한다")
    void getAllEvents_excludesEvictedEvents() {
        buffer.add(ROOM_A, "EVENT", "id=1");
        buffer.add(ROOM_A, "EVENT", "id=2");
        buffer.add(ROOM_A, "EVENT", "id=3");
        buffer.add(ROOM_A, "EVENT", "id=4");  // id=1이 밀려남

        List<BufferedEvent> result = buffer.getAllEvents(ROOM_A);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(2);
        assertThat(result.get(2).id()).isEqualTo(4);
    }

    @Test
    @DisplayName("clear() 후에는 빈 리스트를 반환하고 ID가 1부터 다시 시작한다")
    void clear_removesBufferAndResetsId() {
        buffer.add(ROOM_A, "EVENT", "data");  // id=1
        buffer.add(ROOM_A, "EVENT", "data");  // id=2

        buffer.clear(ROOM_A);

        assertThat(buffer.getEventsAfter(ROOM_A, 0)).isEmpty();

        long newId = buffer.add(ROOM_A, "EVENT", "data");
        assertThat(newId).isEqualTo(1);
    }

    @Test
    @DisplayName("clear()는 해당 방만 삭제하고 다른 방에 영향을 주지 않는다")
    void clear_doesNotAffectOtherRooms() {
        buffer.add(ROOM_A, "EVENT", "data");  // id=1
        buffer.add(ROOM_B, "EVENT", "data");  // id=1

        buffer.clear(ROOM_A);

        assertThat(buffer.getEventsAfter(ROOM_A, 0)).isEmpty();
        assertThat(buffer.getEventsAfter(ROOM_B, 0)).hasSize(1);
    }
}
