package com.hot6ix.upbid.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SmsVerifyRequestDto(
        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 전화번호 형식이 아닙니다.")
        String phoneNumber,

        @NotBlank(message = "인증번호는 필수입니다.")
        @Pattern(regexp = "^[0-9]{6}$", message = "인증번호는 6자리 숫자입니다.")
        String code
) {
}
