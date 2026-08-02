package com.hot6ix.upbid.domain.sse.controller;

import com.hot6ix.upbid.domain.sse.api.SseApi;
import com.hot6ix.upbid.domain.sse.service.SseService;
import com.hot6ix.upbid.global.interceptor.GuestAllowed;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SseController implements SseApi {
    private final SseService sseService;

    @GuestAllowed
    @RequestMapping(value = "/auction-rooms/{roomId}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long roomId){
        return sseService.subscribe(roomId);
    }
}
