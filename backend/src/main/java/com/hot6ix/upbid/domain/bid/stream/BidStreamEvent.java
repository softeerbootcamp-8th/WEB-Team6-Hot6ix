package com.hot6ix.upbid.domain.bid.stream;

import java.util.Map;

/** Redis Stream에 기록된 입찰 파이프라인 이벤트. */
public sealed interface BidStreamEvent {

    String BID_ACCEPTED = "BID_ACCEPTED";
    String ITEM_CLOSING = "ITEM_CLOSING";
    String ITEM_CLOSE_ADVANCED = "ITEM_CLOSE_ADVANCED";

    static BidStreamEvent from(Map<String, String> fields) {
        String type = required(fields, "type");
        return switch (type) {
            case BID_ACCEPTED -> new BidAccepted(
                    required(fields, "requestId"), number(fields, "itemId"),
                    number(fields, "roomId"), number(fields, "bidderUserId"),
                    number(fields, "amount"), number(fields, "acceptedAt"),
                    number(fields, "endAt"), integer(fields, "extendedSeconds"),
                    integer(fields, "totalExtensionSeconds"));
            case ITEM_CLOSING -> new ItemClosing(
                    number(fields, "itemId"), number(fields, "roomId"),
                    number(fields, "currentPrice"), optionalNumber(fields, "leaderUserId"),
                    number(fields, "endAt"), integer(fields, "totalExtensionSeconds"),
                    required(fields, "closeReason"), number(fields, "closedAt"));
            case ITEM_CLOSE_ADVANCED -> new ItemCloseAdvanced(
                    number(fields, "itemId"), number(fields, "roomId"),
                    number(fields, "endAt"), integer(fields, "remainingSeconds"),
                    number(fields, "advancedAt"));
            default -> throw new IllegalArgumentException("지원하지 않는 Stream event type: " + type);
        };
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

    private static Long optionalNumber(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
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

    record ItemClosing(
            long itemId,
            long roomId,
            long currentPrice,
            Long leaderUserId,
            long endAtMillis,
            int totalExtensionSeconds,
            String closeReason,
            long closedAtMillis
    ) implements BidStreamEvent {
    }

    record ItemCloseAdvanced(
            long itemId,
            long roomId,
            long endAtMillis,
            int remainingSeconds,
            long advancedAtMillis
    ) implements BidStreamEvent {
    }
}
