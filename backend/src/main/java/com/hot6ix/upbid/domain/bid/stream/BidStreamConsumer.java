package com.hot6ix.upbid.domain.bid.stream;

import com.hot6ix.upbid.domain.bid.config.BidStreamProperties;
import com.hot6ix.upbid.domain.bid.service.BidStreamPersistenceService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Redis Stream 승인 이벤트를 MySQL에 at-least-once로 전달한다. */
@Component
@RequiredArgsConstructor
public class BidStreamConsumer {

    private final StringRedisTemplate redis;
    private final BidStreamPersistenceService persistenceService;
    private final BidStreamProperties properties;
    private final BidStreamMetrics metrics;

    /**
     * 한 tick에서 Pending 또는 새 이벤트 한 건만 처리한다.
     *
     * <p>예외를 잡아 다음 레코드로 넘어가지 않는다. DB 반영이나 ACK가 실패한 레코드는 PEL에
     * 남아 다음 tick의 최우선 대상이 된다. Scheduled 실행기는 예외를 기록한 뒤 다음 tick을
     * 계속 실행한다.
     */
    @Scheduled(fixedDelayString = "${upbid.bid-stream.poll-delay:100ms}")
    @SchedulerLock(
            name = "bid-stream-consumer",
            lockAtMostFor = "${upbid.bid-stream.lock-at-most-for:5s}")
    public void poll() {

        MapRecord<String, Object, Object> record = readPendingOrNew();
        if (record == null) {
            return;
        }

        try {
            BidStreamEvent event = BidStreamEvent.from(stringFields(record));
            if (!(event instanceof BidStreamEvent.BidAccepted accepted)) {
                throw new IllegalArgumentException("처리할 수 없는 입찰 Stream 이벤트다");
            }

            metrics.recordPersistence(() -> persistenceService.persist(accepted));

            try {
                Long acknowledged =
                        stream().acknowledge(properties.key(), properties.group(), record.getId());
                if (acknowledged == null || acknowledged != 1L) {
                    throw new IllegalStateException(
                            "입찰 Stream 이벤트를 ACK하지 못했다: " + record.getId().getValue());
                }
            } catch (RuntimeException e) {
                metrics.recordAcknowledgeFailure();
                throw e;
            }

            stream().delete(properties.key(), record.getId());
            metrics.recordSuccess();
        } catch (RuntimeException e) {
            metrics.recordFailure();
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private MapRecord<String, Object, Object> readPendingOrNew() {
        StreamOperations<String, Object, Object> stream = stream();
        PendingMessages pending = stream.pending(
                properties.key(), properties.group(), Range.unbounded(), 1);

        if (!pending.isEmpty()) {
            RecordId id = pending.get(0).getId();
            List<MapRecord<String, Object, Object>> claimed = stream.claim(
                    properties.key(), properties.group(), properties.consumer(), Duration.ZERO, id);
            return claimed.isEmpty() ? null : claimed.getFirst();
        }

        List<MapRecord<String, Object, Object>> records = stream.read(
                Consumer.from(properties.group(), properties.consumer()),
                StreamReadOptions.empty().count(1).block(properties.blockTimeout()),
                StreamOffset.create(properties.key(), ReadOffset.lastConsumed()));
        return records == null || records.isEmpty() ? null : records.getFirst();
    }

    private StreamOperations<String, Object, Object> stream() {
        return redis.opsForStream();
    }

    private static Map<String, String> stringFields(MapRecord<String, Object, Object> record) {
        return record.getValue().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> String.valueOf(entry.getValue())));
    }
}
