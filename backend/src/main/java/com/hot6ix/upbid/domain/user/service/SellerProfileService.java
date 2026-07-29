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
import org.springframework.dao.DataIntegrityViolationException;
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

        // 아래 exists 체크와 저장 사이에는 원자성이 없어 동시 요청 시 둘 다 통과할 수 있다.
        // 실제 방어는 seller_profiles.user_id의 DB 유니크 제약과 아래 catch가 담당하고,
        // 이 체크는 정상 상황에서 더 빠르고 친절한 에러를 주기 위한 것이다.
        if (sellerProfileRepository.existsByUser_UserIdAndDeletedAtIsNull(userId)) {
            throw new ApplicationException(SellerProfileErrorType.DUPLICATE_SELLER_PROFILE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(CommonErrorType.RESOURCE_NOT_FOUND));

        SellerProfile sellerProfile = SellerProfile.from(user, request);

        try {
            // save() 대신 saveAndFlush()를 써서 유니크 제약 위반이 이 메서드 안에서(트랜잭션 커밋 전에) 터지게 한다.
            // 그래야 동시 요청으로 인한 위반도 여기서 잡아 DUPLICATE_SELLER_PROFILE로 변환할 수 있다.
            return SellerProfileResponseDto.from(sellerProfileRepository.saveAndFlush(sellerProfile));
        } catch (DataIntegrityViolationException e) {
            throw new ApplicationException(SellerProfileErrorType.DUPLICATE_SELLER_PROFILE);
        }
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
     * 로그인한 회원의 판매자 프로필을 요청 값으로 전체 교체한다.
     * storeName은 필수이며, 나머지 선택 필드를 생략하면 null로 지워진다.
     *
     * @param userId  수정할 회원의 ID
     * @param request 교체할 판매자 프로필 정보
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
