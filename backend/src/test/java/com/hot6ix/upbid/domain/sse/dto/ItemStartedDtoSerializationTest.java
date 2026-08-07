package com.hot6ix.upbid.domain.sse.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.global.common.ServerTime;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code RoomSseManager.send()}는 {@code SseEmitter}가 REST와 같은
 * {@code HttpMessageConverter}로 직렬화하므로, 여기서 확인하는 직렬화 결과가 실제 SSE 전송
 * payload와 같다(이슈 #183).
 */
@JsonTest
class ItemStartedDtoSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("REST의 endAt과 마찬가지로 SSE의 endedTime에도 오프셋이 실린다")
    void endedTimeHasOffset() {
        ItemStartedDto dto = new ItemStartedDto(1L, "한정판 피규어",
                ServerTime.toOffset(LocalDateTime.of(2026, 8, 4, 21, 0)));

        String json = objectMapper.writeValueAsString(dto);

        assertThat(json).containsPattern("\"endedTime\":\"[^\"]*(Z|[+-]\\d{2}:\\d{2})\"");
    }
}
