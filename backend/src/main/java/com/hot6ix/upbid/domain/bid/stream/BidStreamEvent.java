package com.hot6ix.upbid.domain.bid.stream;

import java.util.Map;

/** Redis Stream에 기록된 입찰 파이프라인 이벤트. */
public sealed interface BidStreamEvent {

    String BID_ACCEPTED = "BID_ACCEPTED";

    static BidStreamEvent from(Map<String, String> fields) {
        String type = required(fields, "type");
        if (!BID_ACCEPTED.equals(type)) {
            throw new IllegalArgumentException("지원하지 않는 Stream event type: " + type);
        }

        return new BidAccepted(
                required(fields, "requestId"),
                number(fields, "itemId"),
                number(fields, "roomId"),
                number(fields, "bidderUserId"),
                number(fields, "amount"),
                number(fields, "acceptedAt"),
                number(fields, "endAt"),
                integer(fields, "extendedSeconds"),
                integer(fields, "totalExtensionSeconds"));
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Stream 필수 필드가 없다: " + name);
        }
        return value;
    }

    private static long number(Map<String, String> fields, String name) {
        String value = required(fields, name);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Stream 숫자 필드 형식이 잘못됐다: " + name, e);
        }
    }

    private static int integer(Map<String, String> fields, String name) {
        String value = required(fields, name);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Stream 숫자 필드 형식이 잘못됐다: " + name, e);
        }
    }

    record BidAccepted(
            String requestId,
            long itemId,
            long roomId,
            long bidderUserId,
            long amount,
            long acceptedAtMillis,
            long endAtMillis,
            int extendedSeconds,
            int totalExtensionSeconds
    ) implements BidStreamEvent {
    }
}
