package com.hot6ix.upbid.domain.sse.controller;

import com.hot6ix.upbid.domain.sse.api.SseApi;
import com.hot6ix.upbid.domain.sse.dto.RecentRoomEventDto;
import com.hot6ix.upbid.domain.sse.service.SseService;
import com.hot6ix.upbid.global.interceptor.GuestAllowed;
import com.hot6ix.upbid.global.response.CommonResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SseController implements SseApi {
    private final SseService sseService;

    @GuestAllowed
    @GetMapping(
            value = "/auction-rooms/share/{shareCode}/subscribe",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(Long userId, @PathVariable String shareCode,
            @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId,
            HttpServletResponse response) {
        // 앞단 nginx가 proxy_buffering 기본값(on)이라 응답을 버퍼에 모았다가 내보낸다.
        // 끝나지 않는 스트림인 SSE는 버퍼가 찰 일이 없어 브라우저에 한 바이트도 가지 않는다.
        // (운영에서 서버는 연결 완료를 찍는데 클라이언트는 헤더조차 못 받았다.)
        // nginx는 이 헤더가 붙은 응답만 버퍼링을 끄므로, 설정 대신 응답에 표시한다.
        response.setHeader("X-Accel-Buffering", "no");

        return sseService.subscribe(userId, shareCode, lastEventId);
    }

    @GuestAllowed
    @GetMapping("/auction-rooms/share/{shareCode}/events")
    public ResponseEntity<CommonResponse<List<RecentRoomEventDto>>> getRecentEvents(
            @PathVariable String shareCode) {

        List<RecentRoomEventDto> events = sseService.getRecentEvents(shareCode);

        return ResponseEntity.ok(CommonResponse.ok(events, "최근 경매방 이벤트 조회에 성공했습니다."));
    }
}
