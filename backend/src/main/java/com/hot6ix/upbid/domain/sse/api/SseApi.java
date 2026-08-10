package com.hot6ix.upbid.domain.sse.api;

import com.hot6ix.upbid.domain.sse.dto.RecentRoomEventDto;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "SSE", description = "경매방 실시간 이벤트 구독 API")
public interface SseApi {

    @Operation(
            summary = "경매방 SSE 구독",
            description = "경매방 입장 시 SSE 커넥션을 맺는다. 구독 즉시 현재 리더보드 데이터를 초기 이벤트로 받고, "
                    + "이후 입찰·낙찰·참여자 수 변경 등의 이벤트를 실시간으로 수신한다.\n\n"
                    + "인증이 필요 없는 공개 경로라 경매방을 숫자 ID가 아닌 공유 코드로 지목한다. "
                    + "숫자 PK를 받으면 공유 링크 없이도 남의 방 실시간 이벤트를 순회 구독할 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구독 성공, text/event-stream 연결 유지"),
            @ApiResponse(responseCode = "404", description = "해당 공유 코드의 경매방이 없거나 삭제됨 (code 4002)")
    })
    SseEmitter subscribe(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "구독할 경매방의 공유 코드", required = true)
            @PathVariable String shareCode,
            @Parameter(description = "재연결 시 마지막으로 수신한 이벤트 ID. 최초 연결 시 생략.")
            @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId,
            @Parameter(hidden = true) HttpServletResponse response);

    @Operation(
            summary = "경매방 최근 이벤트 조회",
            description = "새로고침 등으로 SSE가 처음부터 다시 연결될 때, 알림 피드를 채울 "
                    + "최근 이벤트를 최대 20개까지 반환한다. 입찰·시작·마감·낙찰 등 실제 알림 대상 "
                    + "이벤트만 포함하고, 참여자 수 갱신 등 신호성 이벤트는 제외한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "해당 공유 코드의 경매방이 없거나 삭제됨 (code 4002)")
    })
    ResponseEntity<CommonResponse<List<RecentRoomEventDto>>> getRecentEvents(
            @Parameter(description = "조회할 경매방의 공유 코드", required = true)
            @PathVariable String shareCode);
}
