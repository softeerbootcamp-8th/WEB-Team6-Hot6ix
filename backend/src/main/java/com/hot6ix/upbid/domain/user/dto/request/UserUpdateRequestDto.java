package com.hot6ix.upbid.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequestDto(

        @NotBlank(message = "닉네임은 필수 값입니다.")
        @Size(max = 10, message = "닉네임은 10자 이하여야 합니다.")
        String nickname,

        // null이 이 필드의 유효한 값이다 — 프로필 사진을 지운다는 뜻으로 받아 그대로 저장한다.
        // 표시하지 않으면 OpenAPI 문서가 String으로만 나가서, 그 문서로 만든 클라이언트가
        // null을 못 보내는 타입을 갖는다.
        @Schema(types = {"string", "null"})
        String profileImageUrl
) {
}
