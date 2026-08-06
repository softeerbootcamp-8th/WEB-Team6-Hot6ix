package com.hot6ix.upbid.domain.user.service;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.auth.domain.PendingSignup;
import com.hot6ix.upbid.domain.upload.ImageUrlValidator;
import com.hot6ix.upbid.domain.user.dto.request.UserUpdateRequestDto;
import com.hot6ix.upbid.domain.user.dto.response.UserResponseDto;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.UserErrorType;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ImageUrlValidator imageUrlValidator;

    @Transactional(readOnly = true)
    public Optional<Long> findByOAuth(OauthProvider provider, String providerId) {

        return userRepository.findByProviderAndProviderId(provider, providerId)
                .map(User::getUserId);
    }

    @Transactional
    public Long create(PendingSignup pendingSignup) {

        User user = userRepository.saveAndFlush(User.ofPendingSignup(pendingSignup));

        return user.getUserId();
    }

    @Transactional(readOnly = true)
    public UserResponseDto getMe(Long userId) {

        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorType.USER_NOT_FOUND));

        return UserResponseDto.from(user);
    }

    @Transactional
    public UserResponseDto updateMe(Long userId, UserUpdateRequestDto request) {

        imageUrlValidator.validate(request.profileImageUrl());

        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorType.USER_NOT_FOUND));

        user.updateProfile(request.nickname(), request.profileImageUrl());

        return UserResponseDto.from(user);
    }
}
