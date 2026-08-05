package com.hot6ix.upbid.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequestDto(

        @NotBlank(message = "닉네임은 필수 값입니다.")
        @Size(max = 10, message = "닉네임은 10자 이하여야 합니다.")
        String nickname,

        String profileImageUrl
) {
}
