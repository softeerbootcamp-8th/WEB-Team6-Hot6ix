package com.hot6ix.upbid.test;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "테스트 응답 DTO")
public record TestDto(

        @Schema(
                description = "응답 메시지",
                example = "UpBid Server is running"
        )
        String name
) {
}
