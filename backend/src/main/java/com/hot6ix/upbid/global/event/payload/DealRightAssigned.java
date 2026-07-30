package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.ItemEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 낙찰 권한이 특정 후보에게 넘어간 이벤트. 마감 직후 1순위에게, 이후 거래가 실패할 때마다
 * 차순위에게 발행되므로 한 물품에서 여러 번 나간다. 거래 성사를 알리는
 * {@code WinnerDecided}와 구분한다. {@code ItemEnded}로 대신할 수 없는 이유는 회원 ID가
 * 없고 마감 시점에 한 번만 발행되기 때문이다.
 */
public record DealRightAssigned(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt,
        Long dealCandidateId,
        Integer candidateRank,
        Long bidderUserId,
        Long bidAmount
) implements ItemEvent {

    public DealRightAssigned {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(dealCandidateId, "dealCandidateId");
        Objects.requireNonNull(candidateRank, "candidateRank");
        Objects.requireNonNull(bidderUserId, "bidderUserId");
        Objects.requireNonNull(bidAmount, "bidAmount");
    }

    public static DealRightAssigned of(Long roomId, Long itemId, Long dealCandidateId, Integer candidateRank,
                                       Long bidderUserId, Long bidAmount, LocalDateTime occurredAt) {
        return new DealRightAssigned(UUID.randomUUID().toString(), EventType.DEAL_RIGHT_ASSIGNED,
                roomId, itemId, occurredAt, dealCandidateId, candidateRank, bidderUserId, bidAmount);
    }
}
