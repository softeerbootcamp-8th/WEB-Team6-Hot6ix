package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionRoomService {

    private static final String SHARE_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final int SHARE_CODE_LENGTH = 16;
    private static final int SHARE_CODE_MAX_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();

    private final AuctionRoomRepository auctionRoomRepository;
    private final SellerProfileRepository sellerProfileRepository;

    /**
     * 판매자의 경매방을 생성한다. share_code는 서버가 내부적으로 발급하며(충돌 시 재시도),
     * 이를 노출하는 API는 이 서비스가 아닌 별도 PR 소관이다.
     *
     * @param userId  생성을 요청한 회원의 ID
     * @param request 생성할 경매방 정보
     * @return 생성된 경매방
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND)
     */
    @Transactional
    public AuctionRoomResponseDto create(Long userId, AuctionRoomCreateRequestDto request) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        AuctionRoom auctionRoom = saveWithUniqueShareCode(sellerProfile, request);

        return AuctionRoomResponseDto.from(auctionRoom);
    }

    private AuctionRoom saveWithUniqueShareCode(SellerProfile sellerProfile, AuctionRoomCreateRequestDto request) {
        for (int attempt = 0; attempt < SHARE_CODE_MAX_ATTEMPTS; attempt++) {
            AuctionRoom auctionRoom = AuctionRoom.from(sellerProfile, request, generateShareCode());
            try {
                return auctionRoomRepository.saveAndFlush(auctionRoom);
            } catch (DataIntegrityViolationException e) {
                // share_code 충돌 — 다음 시도에서 새 코드로 재시도
            }
        }
        throw new ApplicationException(CommonErrorType.INTERNAL_SERVER_ERROR);
    }

    private String generateShareCode() {
        StringBuilder code = new StringBuilder(SHARE_CODE_LENGTH);
        for (int i = 0; i < SHARE_CODE_LENGTH; i++) {
            code.append(SHARE_CODE_ALPHABET.charAt(secureRandom.nextInt(SHARE_CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private SellerProfile findActiveSellerProfile(Long userId) {
        return sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));
    }
}
