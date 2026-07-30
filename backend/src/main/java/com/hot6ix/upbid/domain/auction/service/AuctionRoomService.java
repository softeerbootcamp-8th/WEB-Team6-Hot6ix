package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
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
    private final AuctionItemRepository auctionItemRepository;
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

        return AuctionRoomResponseDto.from(auctionRoom, countItems(auctionRoom.getAuctionRoomId()));
    }

    /**
     * 경매방 공개 정보를 조회한다. 인증이 필요 없으며, BEFORE를 포함한 모든 상태에서
     * 동일하게 노출한다(상태별 분기 없음).
     *
     * @param auctionRoomId 조회할 경매방의 ID
     * @return 조회된 경매방
     * @throws ApplicationException 경매방이 없거나 soft delete 되었을 때(AUCTION_ROOM_NOT_FOUND)
     */
    public AuctionRoomResponseDto getRoom(Long auctionRoomId) {

        AuctionRoom auctionRoom = auctionRoomRepository.findByAuctionRoomIdAndDeletedAtIsNull(auctionRoomId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        return AuctionRoomResponseDto.from(auctionRoom, countItems(auctionRoomId));
    }

    /**
     * 소유자 본인의 경매방 설정을 부분 수정한다. 요청에서 생략된(null) 필드는 기존 값을 유지한다.
     * "경매 시작 전"만 허용하는데, 이번 PR에서는 방 생성 즉시 "시작"으로 간주하므로 존재·권한
     * 확인까지는 정상 동작하되 이후 단계에서 항상 거절된다. 실제 조건부 허용 로직은
     * [x06-물품-시작] PR에서 재정의한다.
     *
     * @param userId        수정을 요청한 회원의 ID
     * @param auctionRoomId 수정할 경매방의 ID
     * @param request       부분 수정할 경매방 정보
     * @return 수정된 경매방
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               경매방이 없거나 본인 소유가 아닐 때(AUCTION_ROOM_NOT_FOUND),
     *                               (이번 PR에서는 항상) 경매 시작으로 간주될 때(AUCTION_ROOM_ALREADY_STARTED)
     */
    @Transactional
    public AuctionRoomResponseDto update(Long userId, Long auctionRoomId, AuctionRoomUpdateRequestDto request) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        AuctionRoom auctionRoom = findOwnedRoom(sellerProfile, auctionRoomId);
        assertNotStarted();

        auctionRoom.update(request);

        return AuctionRoomResponseDto.from(auctionRoom, countItems(auctionRoomId));
    }

    private AuctionRoom findOwnedRoom(SellerProfile sellerProfile, Long auctionRoomId) {
        return auctionRoomRepository
                .findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                        auctionRoomId, sellerProfile.getSellerProfileId())
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));
    }

    private void assertNotStarted() {
        // 이번 PR에서는 방 생성 즉시 "시작"으로 간주 — 실제 조건부 판정은 x06-물품-시작에서 재정의
        throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_ALREADY_STARTED);
    }

    private long countItems(Long auctionRoomId) {
        return auctionItemRepository.countByAuctionRoom_AuctionRoomId(auctionRoomId);
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
