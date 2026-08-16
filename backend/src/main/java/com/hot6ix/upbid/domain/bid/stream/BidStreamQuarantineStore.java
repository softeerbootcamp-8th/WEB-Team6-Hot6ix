package com.hot6ix.upbid.domain.bid.stream;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 실패 원문을 격리 Stream과 중복 방지 Index에 함께 기록한다. */
@Component
public class BidStreamQuarantineStore {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> quarantineScript;

    public BidStreamQuarantineStore(
            StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.quarantineScript = new DefaultRedisScript<>(
                readScript("lua/quarantine-bid-stream.lua"), List.class);
    }

    public QuarantineResult quarantine(
            String originalStreamKey,
            RecordId originalRecordId,
            Map<String, String> rawFields,
            BidStreamFailure failure,
            long deliveryCount,
            String consumerName,
            long quarantinedAtMillis,
            Throwable error) {
        @SuppressWarnings("unchecked")
        List<String> result = redis.execute(
                quarantineScript,
                List.of(quarantineKey(originalStreamKey), indexKey(originalStreamKey)),
                originalStreamKey,
                originalRecordId.getValue(),
                eventType(rawFields),
                writeJson(rawFields),
                failure.kind().name(),
                failure.code().name(),
                error.getClass().getName(),
                errorMessage(error),
                String.valueOf(deliveryCount),
                String.valueOf(quarantinedAtMillis),
                consumerName);

        if (result == null || result.size() != 2) {
            throw new IllegalStateException("격리 Lua가 잘못된 결과를 반환했다: " + result);
        }
        return new QuarantineResult(result.get(0), "1".equals(result.get(1)));
    }

    public boolean isQuarantined(String originalStreamKey, RecordId originalRecordId) {
        return Boolean.TRUE.equals(redis.opsForHash()
                .hasKey(indexKey(originalStreamKey), originalRecordId.getValue()));
    }

    public static String quarantineKey(String originalStreamKey) {
        return originalStreamKey + ":quarantine";
    }

    public static String indexKey(String originalStreamKey) {
        return quarantineKey(originalStreamKey) + ":index";
    }

    private String writeJson(Map<String, String> fields) {
        return objectMapper.writeValueAsString(fields);
    }

    private static String eventType(Map<String, String> fields) {
        String value = fields.get("type");
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private static String errorMessage(Throwable error) {
        String value = error.getMessage();
        if (value == null) {
            return "";
        }
        return value.substring(0, Math.min(value.length(), MAX_ERROR_MESSAGE_LENGTH));
    }

    private static String readScript(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(path + "를 읽지 못했다", e);
        }
    }

    public record QuarantineResult(String recordId, boolean created) {
    }
}
