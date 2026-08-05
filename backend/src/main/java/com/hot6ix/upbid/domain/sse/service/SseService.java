package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.auction.service.AuctionParticipantService;
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
    private final AuctionParticipantService auctionParticipantService;
    /** 구독 경로도 공개 경로라 숫자 PK를 받지 않는다. 공유 코드를 방 ID로 바꿔서 쓴다. */
    private final AuctionRoomShareService auctionRoomShareService;
    private static final String PARTICIPANT_JOINED_EVENT = "PARTICIPANT_JOINED_EVENT";

    // 방을 구독한다. 로그인 사용자면 구독을 시작하기 전에 참여 기록을 남긴다.
    public SseEmitter subscribe(Long userId, String shareCode){
        Long roomId = auctionRoomShareService.resolveRoomId(shareCode);

        try {
            auctionParticipantService.record(roomId, userId);
        } catch (RuntimeException e) {
            log.warn("참여 기록 실패: roomId={}, userId={}", roomId, userId, e);
        }

        return roomSseManager.subscribe(PARTICIPANT_JOINED_EVENT, roomId, LeaderboardDto.dummy());
    }
}
