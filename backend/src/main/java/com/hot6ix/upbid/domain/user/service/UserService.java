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
    private final SellerProfileService sellerProfileService;

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

    /**
     * 회원탈퇴 처리. User 익명화·soft delete와 SellerProfile 정리를 한 트랜잭션에서
     * 원자적으로 커밋한다 — 하나만 성공하면 "탈퇴는 됐는데 SellerProfile은 살아있다"는
     * 두 갈래 상태가 생기기 때문이다.
     *
     * @throws ApplicationException 존재하지 않거나 이미 탈퇴한 회원일 때(USER_NOT_FOUND),
     *                               진행 중인 경매방이 있을 때(SELLER_PROFILE_IN_USE)
     */
    @Transactional
    public void withdraw(Long userId) {

        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorType.USER_NOT_FOUND));

        user.withdraw();
        sellerProfileService.withdraw(userId);
    }
}
