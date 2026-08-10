package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.auction.service.AuctionRoomShareService;
import com.hot6ix.upbid.domain.sse.dto.RecentRoomEventDto;
import com.hot6ix.upbid.global.event.EventType;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseService {
    private final RoomSseManager roomSseManager;
    /** 구독 경로도 공개 경로라 숫자 PK를 받지 않는다. 공유 코드를 방 ID로 바꿔서 쓴다. */
    private final AuctionRoomShareService auctionRoomShareService;
    private final SseEventBuffer sseEventBuffer;

    /** 화면에 미리 채워줄 최근 알림 개수. */
    private static final int RECENT_EVENTS_LIMIT = 20;

    /**
     * 알림 피드에 실제로 쌓이는 이벤트만 추린다.
     */
    private static final Set<String> FEED_EVENT_NAMES = Set.of(
            EventType.ITEM_STARTED.name(),
            EventType.ITEM_CLOSING_SOON.name(),
            EventType.BID_PLACED.name(),
            EventType.SOFT_CLOSE_EXTENDED.name(),
            EventType.ITEM_CLOSE_ADVANCED.name(),
            EventType.ITEM_ENDED.name(),
            EventType.ROOM_CLOSED.name());

    // 방을 구독한다. 약관 동의는 /agreement API에서 처리하므로 여기서는 SSE 연결만 담당한다.
    // 종료된 방은 resolveOpenRoomId가 409로 거절한다. 방을 닫을 때 서버가 연결을 끊는데,
    // 그것만으로는 EventSource가 자동으로 다시 붙어서 재접속을 여기서 막아야 한다.
    public SseEmitter subscribe(Long userId, String shareCode, Long lastEventId){
        Long roomId = auctionRoomShareService.resolveOpenRoomId(shareCode);

        return roomSseManager.subscribe(roomId, lastEventId);
    }

    /**
     * 새로고침 등으로 SSE가 처음부터 다시 연결될 때, 알림 피드를 채울 최근 이벤트를
     * 최대 {@value #RECENT_EVENTS_LIMIT}개까지 돌려준다.
     */
    public List<RecentRoomEventDto> getRecentEvents(String shareCode) {
        Long roomId = auctionRoomShareService.resolveRoomId(shareCode);

        List<RecentRoomEventDto> feedEvents = sseEventBuffer.getAllEvents(roomId).stream()
                .filter(event -> FEED_EVENT_NAMES.contains(event.eventName()))
                .map(event -> new RecentRoomEventDto(event.id(), event.eventName(), event.data(), event.occurredAt()))
                .toList();

        int from = Math.max(0, feedEvents.size() - RECENT_EVENTS_LIMIT);
        return feedEvents.subList(from, feedEvents.size());
    }
}
