package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomShareResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경매방 공유(share_code, 공유 링크·QR) 관련 로직을 담당한다.
 * <p>소유자 확인 쿼리를 {@link AuctionRoomService}에서 재사용하지 않고 이 서비스가 직접 들고 있다 —
 * {@code AuctionRoomService → AuctionRoomShareService} 의존이 이미 있어서, 반대 방향을 추가하면
 * 순환 의존이 되기 때문이다.
 */
@Service
public class AuctionRoomShareService {

    private static final String SHARE_CODE_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SHARE_CODE_LENGTH = 16;
    private static final String SHARE_PATH_PREFIX = "/join/";

    private final SecureRandom secureRandom = new SecureRandom();

    private final AuctionRoomRepository auctionRoomRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final String frontendUrl;

    public AuctionRoomShareService(
            AuctionRoomRepository auctionRoomRepository,
            SellerProfileRepository sellerProfileRepository,
            @Value("${app.frontend-url}") String frontendUrl) {

        this.auctionRoomRepository = auctionRoomRepository;
        this.sellerProfileRepository = sellerProfileRepository;
        this.frontendUrl = frontendUrl;
    }

    /**
     * 경매방 공유 코드 후보를 하나 생성한다. QR 스캔·링크 복사로만 쓰이므로 사람이 직접
     * 읽거나 타이핑할 일이 없어, 문자 혼동 방지를 위한 별도 문자셋 제한은 두지 않는다.
     * 유일성은 호출 측(방 저장 시 유니크 제약 위반 재시도)에서 보장한다.
     *
     * @return 16자 영숫자 랜덤 코드
     */
    public String generateCandidateShareCode() {
        StringBuilder code = new StringBuilder(SHARE_CODE_LENGTH);
        for (int i = 0; i < SHARE_CODE_LENGTH; i++) {
            code.append(SHARE_CODE_ALPHABET.charAt(secureRandom.nextInt(SHARE_CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * 소유자 본인의 경매방 공유 링크를 조회한다. 방이 없을 때와 남의 방일 때를 구분하지 않고 모두
     * AUCTION_ROOM_NOT_FOUND로 응답한다 — 구분하면 남의 방 ID를 넣어보는 것만으로 그 방의 존재
     * 여부를 알아낼 수 있기 때문이다.
     *
     * @param userId        조회를 요청한 회원의 ID
     * @param auctionRoomId 공유 정보를 조회할 경매방의 ID
     * @return 공유 링크
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                              경매방이 없거나 본인 소유가 아닐 때(AUCTION_ROOM_NOT_FOUND)
     */
    @Transactional(readOnly = true)
    public AuctionRoomShareResponseDto getShareInfo(Long userId, Long auctionRoomId) {

        SellerProfile sellerProfile = sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));

        AuctionRoom auctionRoom = auctionRoomRepository
                .findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                        auctionRoomId, sellerProfile.getSellerProfileId())
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        return new AuctionRoomShareResponseDto(buildShareUrl(auctionRoom.getShareCode()));
    }

    private String buildShareUrl(String shareCode) {
        String baseUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;

        return baseUrl + SHARE_PATH_PREFIX + shareCode;
    }
}
