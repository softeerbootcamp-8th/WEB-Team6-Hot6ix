package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.auction.service.AuctionRoomShareService;
import com.hot6ix.upbid.domain.sse.dto.LeaderboardDto;
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
    private static final String PARTICIPANT_JOINED_EVENT = "PARTICIPANT_JOINED_EVENT";

    // 방을 구독한다. 약관 동의는 /agreement API에서 처리하므로 여기서는 SSE 연결만 담당한다.
    public SseEmitter subscribe(Long userId, String shareCode, Long lastEventId){
        Long roomId = auctionRoomShareService.resolveRoomId(shareCode);

        return roomSseManager.subscribe(PARTICIPANT_JOINED_EVENT, roomId, LeaderboardDto.dummy(), lastEventId);
    }
}
