package com.hot6ix.upbid.domain.bid.stream;

import com.hot6ix.upbid.domain.bid.config.BidStreamProperties;
import com.hot6ix.upbid.domain.bid.service.BidStreamPersistenceService;
import com.hot6ix.upbid.domain.bid.stream.BidStreamMetrics.DrainLimit;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(
        prefix = "upbid.bid-stream",
        name = "consumer-enabled",
        havingValue = "true",
        matchIfMissing = true)
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

    /** Pending 우선 단건 읽기를 유지하면서 설정한 건수 또는 시간 상한까지 연속 처리한다. */
    @Scheduled(fixedDelayString = "${upbid.bid-stream.poll-delay:100ms}")
    @SchedulerLock(
            name = "bid-stream-consumer",
            lockAtMostFor = "${upbid.bid-stream.lock-at-most-for:60s}")
    public void poll() {
        long startedAt = clock.millis();
        int succeeded = 0;
        DrainLimit limit = DrainLimit.NONE;
        RuntimeException failure = null;

        try {
            while (true) {
                Delivery delivery = readPendingOrNew();
                if (delivery == null) {
                    if (succeeded == 0) {
                        metrics.recordIdleLag();
                    }
                    break;
                }

                process(delivery);
                succeeded++;

                if (succeeded >= properties.maxRecordsPerPoll()) {
                    limit = DrainLimit.RECORDS;
                    break;
                }
                if (clock.millis() - startedAt >= properties.maxDrainDuration().toMillis()) {
                    limit = DrainLimit.DURATION;
                    break;
                }
            }
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            if (succeeded > 0) {
                metrics.recordDrain(
                        succeeded,
                        Duration.ofMillis(Math.max(clock.millis() - startedAt, 0)),
                        limit);
            }
            if (failure == null) {
                metrics.recordBacklog(streamBacklog());
            }
        }
    }

    /** 한 레코드의 MySQL 반영, ACK, 삭제를 끝낸 뒤에만 반환한다. */
    private void process(Delivery delivery) {
        MapRecord<String, Object, Object> record = delivery.record();
        Map<String, String> fields = stringFields(record);
        String eventType = fields.get("type");
        metrics.recordLagMillis(clock.millis() - streamTimestamp(record));
        try {
            BidStreamEvent event = BidStreamEvent.from(fields);
            metrics.recordPersistence(() -> persistenceService.persist(event));

            acknowledgeAndDelete(record.getId());
            metrics.recordSuccess(eventType);
        } catch (RuntimeException e) {
            metrics.recordFailure(eventType);
            BidStreamFailure failure = failureClassifier.classify(
                    e, delivery.deliveryCount(), properties.maxFastAttempts());
            if (delivery.deliveryCount() > 1) {
                metrics.recordRetry(failure.kind().name().toLowerCase(Locale.ROOT));
            }
            if (!failure.shouldQuarantine()
                    || !quarantineAndRelease(delivery, fields, eventType, failure, e)) {
                throw e;
            }
        }
    }

    /**
     * 격리에 성공한 레코드를 원본 Stream에서 놓아준다. 놓아줬으면 {@code true}다.
     *
     * <p>격리는 <b>자동 처리를 포기하고 사람이 본다</b>는 결론이라 원본을 PEL에 남겨 둘 이유가
     * 없다. 남겨 두면 {@link #readPendingOrNew()}가 PEL의 가장 오래된 레코드부터 집는 탓에 그
     * 레코드가 계속 다시 집히고, <b>뒤에 쌓인 입찰과 마감이 전부 MySQL에 반영되지 않는다</b>(#374).
     * 실제로 삭제된 물품의 마감 레코드 하나가 40분 동안 뒤의 27건을 막았다.
     *
     * <p>격리 기록이나 ACK가 실패하면 놓아주지 않고 {@code false}를 돌려준다. 원본이 PEL에 남아
     * 다음 전달에서 다시 시도되고, 격리 기록은 Index로 멱등이라 두 번 쌓이지 않는다.
     */
    private boolean quarantineAndRelease(
            Delivery delivery,
            Map<String, String> fields,
            String eventType,
            BidStreamFailure failure,
            RuntimeException cause) {

        RecordId recordId = delivery.record().getId();
        try {
            BidStreamQuarantineStore.QuarantineResult quarantine = quarantineStore.quarantine(
                    properties.key(), recordId, fields, failure,
                    delivery.deliveryCount(), properties.consumer(), clock.millis(), cause);
            if (quarantine.created()) {
                metrics.recordQuarantined(eventType, failure.code().name());
                log.error(
                        "입찰 Stream 이벤트를 격리했다. recordId={}, eventType={}, "
                                + "deliveryCount={}, failureKind={}, failureCode={}",
                        recordId.getValue(), eventType,
                        delivery.deliveryCount(), failure.kind(), failure.code(), cause);
            }
        } catch (RuntimeException quarantineFailure) {
            metrics.recordQuarantineWriteFailure();
            cause.addSuppressed(quarantineFailure);
            log.error(
                    "입찰 Stream 격리 기록에 실패했다. recordId={}, eventType={}, "
                            + "deliveryCount={}, failureKind={}",
                    recordId.getValue(), eventType,
                    delivery.deliveryCount(), failure.kind(), quarantineFailure);
            return false;
        }

        try {
            acknowledgeAndDelete(recordId);
        } catch (RuntimeException acknowledgeFailure) {
            cause.addSuppressed(acknowledgeFailure);
            log.error(
                    "격리한 입찰 Stream 이벤트를 놓아주지 못했다. 다음 전달에서 다시 시도한다. "
                            + "recordId={}, eventType={}",
                    recordId.getValue(), eventType, acknowledgeFailure);
            return false;
        }
        return true;
    }

    /** ACK가 실제로 한 건을 처리했는지 확인한 뒤에만 원본을 지운다. */
    private void acknowledgeAndDelete(RecordId recordId) {
        try {
            Long acknowledged =
                    stream().acknowledge(properties.key(), properties.group(), recordId);
            if (acknowledged == null || acknowledged != 1L) {
                throw new IllegalStateException(
                        "입찰 Stream 이벤트를 ACK하지 못했다: " + recordId.getValue());
            }
        } catch (RuntimeException e) {
            metrics.recordAcknowledgeFailure();
            throw e;
        }
        stream().delete(properties.key(), recordId);
    }

    private long streamBacklog() {
        Long size = stream().size(properties.key());
        return size == null ? 0 : size;
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
