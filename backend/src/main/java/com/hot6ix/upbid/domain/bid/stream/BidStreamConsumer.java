package com.hot6ix.upbid.domain.bid.stream;

import com.hot6ix.upbid.domain.bid.config.BidStreamProperties;
import com.hot6ix.upbid.domain.bid.service.BidStreamPersistenceService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
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
@Slf4j
public class BidStreamConsumer {

    private final StringRedisTemplate redis;
    private final BidStreamPersistenceService persistenceService;
    private final BidStreamProperties properties;
    private final BidStreamMetrics metrics;
    private final BidStreamFailureClassifier failureClassifier;
    private final BidStreamQuarantineStore quarantineStore;
    private final Clock clock;

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
            lockAtMostFor = "${upbid.bid-stream.lock-at-most-for:60s}")
    public void poll() {

        Delivery delivery = readPendingOrNew();
        if (delivery == null) {
            metrics.recordLagMillis(0);
            return;
        }

        MapRecord<String, Object, Object> record = delivery.record();
        Map<String, String> fields = stringFields(record);
        String eventType = fields.get("type");
        metrics.recordLagMillis(clock.millis() - streamTimestamp(record));
        try {
            BidStreamEvent event = BidStreamEvent.from(fields);
            metrics.recordPersistence(() -> persistenceService.persist(event));

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
            metrics.recordSuccess(eventType);
        } catch (RuntimeException e) {
            metrics.recordFailure(eventType);
            BidStreamFailure failure = failureClassifier.classify(
                    e, delivery.deliveryCount(), properties.maxFastAttempts());
            if (delivery.deliveryCount() > 1) {
                metrics.recordRetry(failure.kind().name().toLowerCase(Locale.ROOT));
            }
            if (failure.shouldQuarantine()) {
                try {
                    BidStreamQuarantineStore.QuarantineResult quarantine =
                            quarantineStore.quarantine(
                                    properties.key(), record.getId(), fields, failure,
                                    delivery.deliveryCount(), properties.consumer(),
                                    clock.millis(), e);
                    if (quarantine.created()) {
                        metrics.recordQuarantined(eventType, failure.code().name());
                        log.error(
                                "입찰 Stream 이벤트를 격리했다. recordId={}, eventType={}, "
                                        + "deliveryCount={}, failureKind={}, failureCode={}",
                                record.getId().getValue(), eventType,
                                delivery.deliveryCount(), failure.kind(), failure.code(), e);
                    }
                } catch (RuntimeException quarantineFailure) {
                    metrics.recordQuarantineWriteFailure();
                    e.addSuppressed(quarantineFailure);
                    log.error(
                            "입찰 Stream 격리 기록에 실패했다. recordId={}, eventType={}, "
                                    + "deliveryCount={}, failureKind={}",
                            record.getId().getValue(), eventType,
                            delivery.deliveryCount(), failure.kind(), quarantineFailure);
                }
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Delivery readPendingOrNew() {
        StreamOperations<String, Object, Object> stream = stream();
        PendingMessagesSummary pendingSummary = stream.pending(properties.key(), properties.group());
        metrics.recordPending(pendingSummary.getTotalPendingMessages());
        PendingMessages pending = stream.pending(
                properties.key(), properties.group(), Range.unbounded(), 1);

        if (!pending.isEmpty()) {
            var pendingMessage = pending.get(0);
            RecordId id = pendingMessage.getId();
            boolean quarantined = quarantineStore.isQuarantined(properties.key(), id);
            metrics.recordOldestPendingAgeSeconds(
                    (clock.millis() - streamTimestamp(id)) / 1_000);
            metrics.recordHalted(quarantined);
            Duration minimumIdle = quarantined
                    ? properties.haltedRetryDelay()
                    : properties.retryDelay();
            List<MapRecord<String, Object, Object>> claimed = stream.claim(
                    properties.key(), properties.group(), properties.consumer(), minimumIdle, id);
            return claimed.isEmpty()
                    ? null
                    : new Delivery(claimed.getFirst(), pendingMessage.getTotalDeliveryCount() + 1);
        }

        metrics.recordOldestPendingAgeSeconds(0);
        metrics.recordHalted(false);

        List<MapRecord<String, Object, Object>> records = stream.read(
                Consumer.from(properties.group(), properties.consumer()),
                StreamReadOptions.empty().count(1).block(properties.blockTimeout()),
                StreamOffset.create(properties.key(), ReadOffset.lastConsumed()));
        return records == null || records.isEmpty()
                ? null
                : new Delivery(records.getFirst(), 1);
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

    private static long streamTimestamp(MapRecord<String, Object, Object> record) {
        return streamTimestamp(record.getId());
    }

    private static long streamTimestamp(RecordId recordId) {
        String value = recordId.getValue();
        int separator = value.indexOf('-');
        return Long.parseLong(separator < 0 ? value : value.substring(0, separator));
    }

    private record Delivery(
            MapRecord<String, Object, Object> record,
            long deliveryCount
    ) {
    }
}
