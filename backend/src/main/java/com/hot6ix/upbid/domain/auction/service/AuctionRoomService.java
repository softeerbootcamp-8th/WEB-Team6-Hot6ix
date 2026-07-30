package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager transactionManager;

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
    public AuctionRoomPublicResponseDto create(Long userId, AuctionRoomCreateRequestDto request) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        AuctionRoom auctionRoom = saveWithUniqueShareCode(sellerProfile, request);

        return AuctionRoomPublicResponseDto.from(auctionRoom, countItems(auctionRoom.getAuctionRoomId()));
    }

    /**
     * 경매방 공개 정보를 조회한다. 인증이 필요 없으며, BEFORE를 포함한 모든 상태에서
     * 동일하게 노출한다(상태별 분기 없음).
     *
     * @param auctionRoomId 조회할 경매방의 ID
     * @return 조회된 경매방
     * @throws ApplicationException 경매방이 없거나 soft delete 되었을 때(AUCTION_ROOM_NOT_FOUND)
     */
    public AuctionRoomPublicResponseDto getRoom(Long auctionRoomId) {

        AuctionRoom auctionRoom = auctionRoomRepository.findByAuctionRoomIdAndDeletedAtIsNull(auctionRoomId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        return AuctionRoomPublicResponseDto.from(auctionRoom, countItems(auctionRoomId));
    }

    /**
     * 소유자 본인의 경매방 설정을 부분 수정한다. 요청에서 생략된(null) 필드는 기존 값을 유지한다.
     * 이 방의 물품 중 하나라도 READY가 아닌 상태로 경매에 올라간 적이 있으면(=경매가 시작된
     * 적 있으면) 이후로도 계속 수정할 수 없다.
     *
     * @param userId        수정을 요청한 회원의 ID
     * @param auctionRoomId 수정할 경매방의 ID
     * @param request       부분 수정할 경매방 정보
     * @return 수정된 경매방
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               경매방이 없거나 본인 소유가 아닐 때(AUCTION_ROOM_NOT_FOUND),
     *                               경매가 시작된 적 있을 때(AUCTION_ROOM_ALREADY_STARTED)
     */
    @Transactional
    public AuctionRoomPublicResponseDto update(Long userId, Long auctionRoomId, AuctionRoomUpdateRequestDto request) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        AuctionRoom auctionRoom = findOwnedRoom(sellerProfile, auctionRoomId);
        assertNotStarted(auctionRoomId);

        auctionRoom.update(request);

        return AuctionRoomPublicResponseDto.from(auctionRoom, countItems(auctionRoomId));
    }

    private AuctionRoom findOwnedRoom(SellerProfile sellerProfile, Long auctionRoomId) {
        return auctionRoomRepository
                .findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                        auctionRoomId, sellerProfile.getSellerProfileId())
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));
    }

    private void assertNotStarted(Long auctionRoomId) {
        if (auctionItemRepository.existsByAuctionRoom_AuctionRoomIdAndStatusNot(auctionRoomId, AuctionItemStatus.READY)) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_ALREADY_STARTED);
        }
    }

    private long countItems(Long auctionRoomId) {
        return auctionItemRepository.countByAuctionRoom_AuctionRoomId(auctionRoomId);
    }

    /**
     * share_code가 충돌하면(극히 드묾) 새 코드로 재시도한다. IDENTITY 전략은 save() 시점에
     * 바로 INSERT가 나가므로, 실패한 시도와 같은 세션에서 그냥 다시 저장을 시도하면
     * Hibernate가 "예외 발생 후에는 세션을 다시 flush하면 안 된다"는 자체 규칙에 걸려
     * DataIntegrityViolationException이 아닌 AssertionFailure를 던지며 재시도가 통째로
     * 깨진다. 그래서 시도 하나당 완전히 새로운 트랜잭션(REQUIRES_NEW)에서 저장해, 이전
     * 시도의 실패가 다음 시도의 세션에 영향을 주지 않게 한다.
     */
    private AuctionRoom saveWithUniqueShareCode(SellerProfile sellerProfile, AuctionRoomCreateRequestDto request) {
        TransactionTemplate newTransactionTemplate = new TransactionTemplate(transactionManager);
        newTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (int attempt = 0; attempt < SHARE_CODE_MAX_ATTEMPTS; attempt++) {
            AuctionRoom auctionRoom = AuctionRoom.from(sellerProfile, request, generateShareCode());
            try {
                return newTransactionTemplate.execute(status -> auctionRoomRepository.saveAndFlush(auctionRoom));
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
