package com.hot6ix.upbid.domain.user.dto.response;

import com.hot6ix.upbid.domain.user.entity.User;

public record UserResponseDto(
        Long userId,
        String nickname,
        String email,
        String profileImageUrl,
        String phoneNumber
) {
    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getUserId(),
                user.getNickname(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getPhoneNumber()
        );
    }
}
