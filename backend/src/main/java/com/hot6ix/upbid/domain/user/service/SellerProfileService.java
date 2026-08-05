package com.hot6ix.upbid.domain.user.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.user.dto.request.SellerProfileCreateRequestDto;
import com.hot6ix.upbid.domain.user.dto.request.SellerProfileUpdateRequestDto;
import com.hot6ix.upbid.domain.user.dto.response.SellerProfileResponseDto;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.upload.ImageUrlValidator;
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
    private final ImageUrlValidator imageUrlValidator;
    /**
     * 삭제를 막을지 판정하기 위해서만 경매 도메인을 읽는다. 판매자가 방송 중인지는 경매방
     * 상태에만 있어 한쪽은 반드시 경계를 넘어야 한다. 읽기만 하고 상태를 바꾸지 않는다
     * (반대 방향인 {@code AuctionRoomService}의 {@code SellerProfileRepository}와 같은 모양).
     */
    private final AuctionRoomRepository auctionRoomRepository;

    /**
     * 판매자 프로필을 등록한다. 회원당 하나만 허용하며, 살아 있는 프로필이 있으면 거절한다.
     *
     * <p><b>삭제했던 회원이 다시 등록하면 그 행을 되살린다.</b> {@code user_id}에 걸린 DB
     * UNIQUE는 {@code deleted_at}을 모르므로 새 행을 넣으면 제약에 걸린다(MySQL에는 부분 유니크
     * 인덱스가 없다). 되살리기를 고른 이유는 제약을 우회하려는 게 아니라 {@code seller_profile_id}가
     * 유지되어야 해서다 — 그 값을 가리키는 경매방·상품·거래가 재등록 뒤에도 그대로 붙는다.
     *
     * @param userId  등록할 회원의 ID
     * @param request 등록할 판매자 프로필 정보
     * @return 등록되거나 되살아난 판매자 프로필
     * @throws ApplicationException 살아 있는 프로필이 이미 있을 때(DUPLICATE_SELLER_PROFILE),
     *                               회원이 존재하지 않거나 탈퇴한 회원일 때(RESOURCE_NOT_FOUND)
     */
    @Transactional
    public SellerProfileResponseDto create(Long userId, SellerProfileCreateRequestDto request) {

        imageUrlValidator.validate(request.storeImageUrl());

        // 되살리는 경로에서도 탈퇴 회원을 걸러야 하므로 분기보다 먼저 조회한다.
        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(CommonErrorType.RESOURCE_NOT_FOUND));

        return sellerProfileRepository.findByUser_UserId(userId)
                .map(existing -> restoreOrReject(existing, request))
                .orElseGet(() -> insert(user, request));
    }

    private SellerProfileResponseDto restoreOrReject(
            SellerProfile sellerProfile, SellerProfileCreateRequestDto request) {

        if (!sellerProfile.isDeleted()) {
            throw new ApplicationException(SellerProfileErrorType.DUPLICATE_SELLER_PROFILE);
        }

        sellerProfile.restore(request);

        return SellerProfileResponseDto.from(sellerProfile);
    }

    /**
     * 조회 시점에는 프로필이 없었어도, 같은 회원의 등록 요청 둘이 겹치면 여기서 UNIQUE에 걸린다.
     * 그 경합을 DB가 막아 주므로 앱에서 따로 잠그지 않는다.
     */
    private SellerProfileResponseDto insert(User user, SellerProfileCreateRequestDto request) {
        try {
            // saveAndFlush로 즉시 flush해야 유니크 제약 위반을 여기서 잡아 변환할 수 있다.
            return SellerProfileResponseDto.from(
                    sellerProfileRepository.saveAndFlush(SellerProfile.from(user, request)));
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
     * 낙찰 후보가 된 구매자가 판매자에게 연락하기 위해 다른 회원의 판매자 프로필을 조회한다.
     *
     * @throws ApplicationException 프로필이 없거나 삭제됐을 때(SELLER_PROFILE_NOT_FOUND)
     */
    public SellerProfileResponseDto getProfile(Long sellerProfileId) {
        return SellerProfileResponseDto.from(
                sellerProfileRepository.findBySellerProfileIdAndDeletedAtIsNull(sellerProfileId)
                        .orElseThrow(() -> new ApplicationException(
                                SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND)));
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

        // 수정은 PUT 전체 교체라 등록과 똑같이 URL이 통째로 들어온다. 등록만 막으면 검증이 없는 것과 같다.
        imageUrlValidator.validate(request.storeImageUrl());

        SellerProfile sellerProfile = findActiveByUserId(userId);
        sellerProfile.update(request);

        return SellerProfileResponseDto.from(sellerProfile);
    }

    /**
     * 로그인한 회원의 판매자 프로필을 soft delete 한다. 경매 이력 보존을 위해 실제 row는 남긴다.
     *
     * <p><b>진행 중인(OPEN) 경매방이 하나라도 있으면 거절한다.</b> 판매자 API는 모두 살아 있는
     * 프로필을 전제로 하므로, 방송 중에 프로필이 사라지면 물품을 시작할 사람도 방을 종료할
     * 사람도 없어져 구매자가 결과를 받지 못한 채로 남는다. 시작 전·종료된 방은 기다리는 사람이
     * 없고, 재등록하면 같은 프로필을 되살리므로({@link #create}) 그대로 돌아온다.
     *
     * @param userId 삭제할 회원의 ID
     * @throws ApplicationException 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               진행 중인 경매방이 있을 때(SELLER_PROFILE_IN_USE)
     */
    @Transactional
    public void delete(Long userId) {

        SellerProfile sellerProfile = findActiveByUserId(userId);

        if (auctionRoomRepository.existsBySellerProfile_SellerProfileIdAndStatusAndDeletedAtIsNull(
                sellerProfile.getSellerProfileId(), AuctionRoomStatus.OPEN)) {
            throw new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_IN_USE);
        }

        sellerProfile.softDelete(LocalDateTime.now());
    }

    private SellerProfile findActiveByUserId(Long userId) {
        return sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));
    }
}
