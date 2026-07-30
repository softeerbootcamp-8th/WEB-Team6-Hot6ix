package com.hot6ix.upbid.domain.sse.dto;

import java.time.LocalDateTime;

public record ItemStartedDto(
        String itemName,
        LocalDateTime endedTime
) {
}
