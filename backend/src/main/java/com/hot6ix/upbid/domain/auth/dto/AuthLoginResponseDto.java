package com.hot6ix.upbid.domain.auth.dto;

import com.hot6ix.upbid.domain.user.dto.UserOAuthLoginDto;

public record AuthLoginResponseDto(
        Long userId,
        String nickname,
        boolean isNewUser
) {
    public static AuthLoginResponseDto from(UserOAuthLoginDto user) {
        return new AuthLoginResponseDto(user.userId(), user.nickname(), user.isNewUser());
    }
}
