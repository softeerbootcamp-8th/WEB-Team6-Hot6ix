package com.hot6ix.upbid.domain.user.service;

import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.domain.auth.dto.OAuthUserInfo;
import com.hot6ix.upbid.domain.user.dto.response.UserMeResponseDto;
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

    @Transactional(readOnly = true)
    public Optional<Long> findByOAuth(OAuthUserInfo userInfo) {

        return userRepository.findByProviderAndProviderId(userInfo.provider(), userInfo.providerId())
                .map(this::verifyNotWithdrawn)
                .map(User::getUserId);
    }

    @Transactional(readOnly = true)
    public UserMeResponseDto getMe(Long userId) {

        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorType.USER_NOT_FOUND));

        return UserMeResponseDto.from(user);
    }

    private User verifyNotWithdrawn(User user) {

        if (user.isDeleted()) {
            throw new ApplicationException(AuthErrorType.WITHDRAWN_USER);
        }

        return user;
    }
}
