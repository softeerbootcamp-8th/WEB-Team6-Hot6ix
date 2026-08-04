package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.auction.service.AuctionParticipantService;
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
    private static final String PARTICIPANT_JOINED_EVENT = "PARTICIPANT_JOINED_EVENT";

    // 방을 구독한다. 로그인 사용자면 구독을 시작하기 전에 참여 기록을 남긴다.
    public SseEmitter subscribe(Long userId, Long roomId){
        try {
            auctionParticipantService.record(roomId, userId);
        } catch (RuntimeException e) {
            log.warn("참여 기록 실패: roomId={}, userId={}", roomId, userId, e);
        }

        return roomSseManager.subscribe(PARTICIPANT_JOINED_EVENT, roomId, LeaderboardDto.dummy());
    }
}
