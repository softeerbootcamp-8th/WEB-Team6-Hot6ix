package com.hot6ix.upbid.domain.user.service;

import com.hot6ix.upbid.domain.user.dto.request.SellerProfileCreateRequestDto;
import com.hot6ix.upbid.domain.user.dto.request.SellerProfileUpdateRequestDto;
import com.hot6ix.upbid.domain.user.dto.response.SellerProfileResponseDto;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerProfileService {

    private final SellerProfileRepository sellerProfileRepository;
    private final UserRepository userRepository;

    /**
     * 판매자 프로필을 등록한다. 회원당 하나만 허용하며, 이미 등록된 프로필이 있으면 거절한다.
     *
     * @param userId  등록할 회원의 ID
     * @param request 등록할 판매자 프로필 정보
     * @return 등록된 판매자 프로필
     * @throws ApplicationException 이미 프로필이 존재하거나(DUPLICATE_SELLER_PROFILE),
     *                               회원이 존재하지 않을 때(RESOURCE_NOT_FOUND)
     */
    @Transactional
    public SellerProfileResponseDto create(Long userId, SellerProfileCreateRequestDto request) {

        if (sellerProfileRepository.existsByUser_UserIdAndDeletedAtIsNull(userId)) {
            throw new ApplicationException(SellerProfileErrorType.DUPLICATE_SELLER_PROFILE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(CommonErrorType.RESOURCE_NOT_FOUND));

        SellerProfile sellerProfile = SellerProfile.builder()
                .user(user)
                .storeName(request.storeName())
                .storeImageUrl(request.storeImageUrl())
                .snsUrl(request.snsUrl())
                .storePhoneNumber(request.storePhoneNumber())
                .storeDescription(request.storeDescription())
                .build();

        return SellerProfileResponseDto.from(sellerProfileRepository.save(sellerProfile));
    }

    /**
     * 로그인한 회원의 판매자 프로필을 조회한다.
     *
     * @param userId 조회할 회원의 ID
     * @return 조회된 판매자 프로필
     * @throws ApplicationException 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND)
     */
    public SellerProfileResponseDto getMyProfile(Long userId) {
        return SellerProfileResponseDto.from(findActiveByUserId(userId));
    }

    /**
     * 로그인한 회원의 판매자 프로필 중 전달된 필드만 부분 수정한다. null인 필드는 변경하지 않는다.
     *
     * @param userId  수정할 회원의 ID
     * @param request 수정할 필드 값 (null이면 기존 값 유지)
     * @return 수정된 판매자 프로필
     * @throws ApplicationException 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND)
     */
    @Transactional
    public SellerProfileResponseDto update(Long userId, SellerProfileUpdateRequestDto request) {

        SellerProfile sellerProfile = findActiveByUserId(userId);
        sellerProfile.update(
                request.storeName(),
                request.storeImageUrl(),
                request.snsUrl(),
                request.storePhoneNumber(),
                request.storeDescription()
        );

        return SellerProfileResponseDto.from(sellerProfile);
    }

    /**
     * 로그인한 회원의 판매자 프로필을 soft delete 한다. 경매 이력 보존을 위해 실제 row는 남긴다.
     *
     * @param userId 삭제할 회원의 ID
     * @throws ApplicationException 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND)
     */
    @Transactional
    public void delete(Long userId) {
        findActiveByUserId(userId).softDelete(LocalDateTime.now());
    }

    private SellerProfile findActiveByUserId(Long userId) {
        return sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));
    }
}
