package com.hot6ix.upbid.domain.user.dto.response;

import com.hot6ix.upbid.domain.user.entity.User;

public record UserMeResponseDto(
        Long userId,
        String nickname,
        String email,
        String profileImageUrl
) {
    public static UserMeResponseDto from(User user) {
        return new UserMeResponseDto(
                user.getUserId(),
                user.getNickname(),
                user.getEmail(),
                user.getProfileImageUrl()
        );
    }
}
