package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.dto.LeaderboardDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class SseService {
    private final RoomSseManager roomSseManager;
    private static final String PARTICIPANT_JOINED_EVENT = "PARTICIPANT_JOINED_EVENT";

    public SseEmitter subscribe(Long roomId){
        return roomSseManager.subscribe(PARTICIPANT_JOINED_EVENT, roomId, LeaderboardDto.dummy());
    }
}
