package com.hot6ix.upbid.domain.sse.api;

import com.hot6ix.upbid.global.interceptor.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "SSE", description = "경매방 실시간 이벤트 구독 API")
public interface SseApi {

    @Operation(
            summary = "경매방 SSE 구독",
            description = "경매방 입장 시 SSE 커넥션을 맺는다. 구독 즉시 현재 리더보드 데이터를 초기 이벤트로 받고, "
                    + "이후 입찰·낙찰·참여자 수 변경 등의 이벤트를 실시간으로 수신한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구독 성공, text/event-stream 연결 유지")
    })
    SseEmitter subscribe(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "구독할 경매방 ID", required = true)
            @PathVariable Long roomId);
}
