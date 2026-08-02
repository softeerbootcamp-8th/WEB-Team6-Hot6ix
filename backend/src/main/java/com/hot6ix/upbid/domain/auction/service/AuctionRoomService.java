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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionRoomService {

    private static final int SHARE_CODE_MAX_ATTEMPTS = 5;

    private final AuctionRoomRepository auctionRoomRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final AuctionRoomShareService auctionRoomShareService;

    /**
     * 판매자의 경매방을 생성한다. share_code는 서버가 내부적으로 발급하며(충돌 시 재시도),
     * 이를 노출하는 API는 이 서비스가 아닌 별도 PR 소관이다.
     * <p>이 메서드 자체는 트랜잭션으로 감싸지 않는다({@code NOT_SUPPORTED}) — {@link #saveWithUniqueShareCode}가
     * 시도마다 {@code saveAndFlush()}를 호출하는데, 감싸는 트랜잭션이 있으면 모든 시도가 같은
     * Hibernate 세션을 공유하게 되고, 첫 충돌 이후에는 세션이 오염돼 재시도가 항상 깨진다
     * (자세한 이유는 {@link #saveWithUniqueShareCode} 참고). 트랜잭션이 없으면 각
     * {@code saveAndFlush()} 호출이 Spring Data 리포지토리 기본 동작으로 자기만의 트랜잭션을
     * 얻어서, 이전 시도의 실패가 다음 시도에 영향을 주지 않는다.
     *
     * @param userId  생성을 요청한 회원의 ID
     * @param request 생성할 경매방 정보
     * @return 생성된 경매방
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuctionRoomPublicResponseDto create(Long userId, AuctionRoomCreateRequestDto request) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        AuctionRoom auctionRoom = saveWithUniqueShareCode(sellerProfile, request);

        // 물품 추가는 별도 PR([x05-경매방-물품-구성])에서 기존 방에만 할 수 있어서, 생성 직후엔
        // itemCount가 항상 0이다 — 조회 없이 바로 0을 넣는다.
        return AuctionRoomPublicResponseDto.from(auctionRoom, 0L);
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
     * 공유 코드로 경매방 공개 정보를 조회한다. 공유 링크를 받은 사람이 방에 진입할 때 쓰며,
     * {@link #getRoom(Long)}과 응답이 동일하다. roomId 대신 share_code로 진입점을 두는 이유는
     * 순차 증가하는 숫자 PK를 공개 URL에 노출하면 남의 방을 추측해 순회 조회할 수 있기 때문이다.
     *
     * @param shareCode 조회할 경매방의 공유 코드
     * @return 조회된 경매방
     * @throws ApplicationException 해당 공유 코드의 경매방이 없거나 soft delete 되었을 때(AUCTION_ROOM_NOT_FOUND)
     */
    public AuctionRoomPublicResponseDto getRoomByShareCode(String shareCode) {

        AuctionRoom auctionRoom = auctionRoomRepository.findByShareCodeAndDeletedAtIsNull(shareCode)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        return AuctionRoomPublicResponseDto.from(auctionRoom, countItems(auctionRoom.getAuctionRoomId()));
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
     * share_code가 충돌하면(극히 드묾) 새 코드로 재시도한다. {@link #create}가 트랜잭션 없이
     * 호출하므로, 이 반복문의 매 {@code saveAndFlush()}가 각각 자기만의 트랜잭션·세션을 얻는다
     * — 그래서 한 시도의 충돌·실패가 다음 시도에 영향을 주지 않는다. (IDENTITY 전략은 save()
     * 시점에 바로 INSERT가 나가는데, 만약 같은 세션에서 재시도했다면 Hibernate가 "예외 발생
     * 후에는 세션을 다시 flush하면 안 된다"는 규칙에 걸려 DataIntegrityViolationException이
     * 아닌 AssertionFailure를 던지며 재시도가 통째로 깨진다 — Testcontainers로 확인된 실제
     * 버그였다.)
     */
    private AuctionRoom saveWithUniqueShareCode(SellerProfile sellerProfile, AuctionRoomCreateRequestDto request) {
        for (int attempt = 0; attempt < SHARE_CODE_MAX_ATTEMPTS; attempt++) {
            AuctionRoom auctionRoom = AuctionRoom.from(
                    sellerProfile, request, auctionRoomShareService.generateCandidateShareCode());
            try {
                return auctionRoomRepository.saveAndFlush(auctionRoom);
            } catch (DataIntegrityViolationException e) {
                // share_code 충돌 — 다음 시도에서 새 코드로 재시도
            }
        }
        throw new ApplicationException(CommonErrorType.INTERNAL_SERVER_ERROR);
    }

    private SellerProfile findActiveSellerProfile(Long userId) {
        return sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));
    }
}
