package com.hot6ix.upbid.domain.user.dto;

import com.hot6ix.upbid.domain.user.entity.User;

public record UserOAuthLoginDto(
        Long userId,
        String nickname,
        boolean isNewUser
) {
    public static UserOAuthLoginDto of(User user, boolean isNewUser) {
        return new UserOAuthLoginDto(user.getUserId(), user.getNickname(), isNewUser);
    }
}
