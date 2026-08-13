package com.hot6ix.upbid.domain.bid.stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.bid.config.BidStreamProperties;
import com.hot6ix.upbid.domain.bid.service.BidStreamPersistenceService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class BidStreamConsumerTest {

    private static final String STREAM = "auction:bid:stream";
    private static final String GROUP = "bid-mysql-writers";
    private static final String CONSUMER = "test-bid-writer";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private StreamOperations<String, Object, Object> streamOperations;
    @Mock
    private BidStreamPersistenceService persistenceService;

    private BidStreamConsumer consumer;

    @BeforeEach
    void setUp() {
        when(redis.opsForStream()).thenReturn(streamOperations);
        BidStreamProperties properties = new BidStreamProperties(
                STREAM, GROUP, CONSUMER, Duration.ofMillis(100), Duration.ofMillis(50),
                Duration.ofSeconds(5));
        consumer = new BidStreamConsumer(
                redis, persistenceService, properties,
                new BidStreamMetrics(new SimpleMeterRegistry()));
    }

    @Test
    @DisplayName("MySQL 반영이 끝난 뒤에만 ACK하고 Stream 레코드를 삭제한다")
    void persistsBeforeAcknowledgeAndDelete() {

        MapRecord<String, Object, Object> record = record("1786636800000-0");
        givenNoPending();
        when(streamOperations.read(
                any(Consumer.class), any(StreamReadOptions.class), anyStreamOffsets()))
                .thenReturn(List.of(record));
        when(streamOperations.acknowledge(STREAM, GROUP, record.getId())).thenReturn(1L);

        consumer.poll();

        InOrder order = inOrder(persistenceService, streamOperations);
        order.verify(persistenceService).persist(any(BidStreamEvent.BidAccepted.class));
        order.verify(streamOperations).acknowledge(STREAM, GROUP, record.getId());
        order.verify(streamOperations).delete(STREAM, record.getId());
    }

    @Test
    @DisplayName("MySQL 반영이 실패하면 ACK와 삭제를 하지 않아 pending으로 남긴다")
    void leavesPendingWhenPersistenceFails() {

        MapRecord<String, Object, Object> record = record("1786636800000-0");
        givenNoPending();
        when(streamOperations.read(
                any(Consumer.class), any(StreamReadOptions.class), anyStreamOffsets()))
                .thenReturn(List.of(record));
        RuntimeException failure = new RuntimeException("DB unavailable");
        org.mockito.Mockito.doThrow(failure)
                .when(persistenceService).persist(any(BidStreamEvent.BidAccepted.class));

        assertThatThrownBy(consumer::poll).isSameAs(failure);

        verify(streamOperations, never()).acknowledge(eq(STREAM), eq(GROUP), any(RecordId.class));
        verify(streamOperations, never()).delete(eq(STREAM), any(RecordId.class));
    }

    @Test
    @DisplayName("ACK가 실패하면 확인되지 않은 레코드를 삭제하지 않는다")
    void doesNotDeleteWhenAcknowledgeFails() {

        MapRecord<String, Object, Object> record = record("1786636800000-0");
        givenNoPending();
        when(streamOperations.read(
                any(Consumer.class), any(StreamReadOptions.class), anyStreamOffsets()))
                .thenReturn(List.of(record));
        RuntimeException failure = new RuntimeException("Redis ACK unavailable");
        when(streamOperations.acknowledge(STREAM, GROUP, record.getId())).thenThrow(failure);

        assertThatThrownBy(consumer::poll).isSameAs(failure);

        verify(persistenceService).persist(any(BidStreamEvent.BidAccepted.class));
        verify(streamOperations, never()).delete(eq(STREAM), any(RecordId.class));
    }

    @Test
    @DisplayName("ACK 결과가 0이면 미확인 원문을 Stream에서 삭제하지 않는다")
    void doesNotDeleteWhenNothingWasAcknowledged() {

        MapRecord<String, Object, Object> record = record("1786636800000-0");
        givenNoPending();
        when(streamOperations.read(
                any(Consumer.class), any(StreamReadOptions.class), anyStreamOffsets()))
                .thenReturn(List.of(record));
        when(streamOperations.acknowledge(STREAM, GROUP, record.getId())).thenReturn(0L);

        assertThatThrownBy(consumer::poll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(record.getId().getValue());

        verify(streamOperations, never()).delete(eq(STREAM), any(RecordId.class));
    }

    @Test
    @DisplayName("pending 이벤트가 있으면 새 이벤트를 읽기 전에 현재 Consumer로 회수한다")
    void claimsPendingBeforeReadingNewEvent() {

        RecordId pendingId = RecordId.of("1786636800000-0");
        PendingMessage pendingMessage = new PendingMessage(
                pendingId, Consumer.from(GROUP, "dead-consumer"), Duration.ofSeconds(10), 1L);
        when(streamOperations.pending(eq(STREAM), eq(GROUP), any(Range.class), anyLong()))
                .thenReturn(new PendingMessages(GROUP, List.of(pendingMessage)));
        MapRecord<String, Object, Object> pendingRecord = record(pendingId.getValue());
        when(streamOperations.claim(STREAM, GROUP, CONSUMER, Duration.ZERO, pendingId))
                .thenReturn(List.of(pendingRecord));
        when(streamOperations.acknowledge(STREAM, GROUP, pendingId)).thenReturn(1L);

        consumer.poll();

        verify(streamOperations).claim(STREAM, GROUP, CONSUMER, Duration.ZERO, pendingId);
        verify(streamOperations, never()).read(
                any(Consumer.class), any(StreamReadOptions.class), anyStreamOffsets());
        verify(persistenceService).persist(any(BidStreamEvent.BidAccepted.class));
    }

    private void givenNoPending() {
        when(streamOperations.pending(eq(STREAM), eq(GROUP), any(Range.class), anyLong()))
                .thenReturn(new PendingMessages(GROUP, List.of()));
    }

    private static MapRecord<String, Object, Object> record(String id) {
        return StreamRecords.<String, Object, Object>mapBacked(Map.of(
                        "type", "BID_ACCEPTED",
                        "requestId", "request-1",
                        "itemId", "101",
                        "roomId", "202",
                        "bidderUserId", "11",
                        "amount", "10000",
                        "acceptedAt", "1786636800000",
                        "endAt", "1786637100000",
                        "extendedSeconds", "60",
                        "totalExtensionSeconds", "180"))
                .withStreamKey(STREAM)
                .withId(RecordId.of(id));
    }

    @SuppressWarnings("unchecked")
    private static StreamOffset<String>[] anyStreamOffsets() {
        return any(StreamOffset[].class);
    }
}
