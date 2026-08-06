package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemResultResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomCountsResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomListItemResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResultResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomRole;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemResultProjection;
import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.domain.deal.repository.MyCandidateRankProjection;
import com.hot6ix.upbid.domain.sse.service.RoomSseManager;
import com.hot6ix.upbid.domain.upload.ImageUrlValidator;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.event.payload.RoomUpdated;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
import com.hot6ix.upbid.global.response.CursorPageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final AuctionParticipantRepository auctionParticipantRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final AuctionRoomShareService auctionRoomShareService;
    private final RoomSseManager roomSseManager;
    /**
     * 결과 화면의 "내 최종 순위"만을 위해 거래 도메인을 읽는다. 낙찰자는 경매 물품에, 순위는
     * 낙찰 후보에 있어 한쪽은 반드시 경계를 넘어야 한다. 읽기만 하고 상태를 바꾸지 않는다.
     */
    private final DealCandidateRepository dealCandidateRepository;
    private final DomainEventPublisher domainEventPublisher;
    /** 커버 주소는 클라이언트가 문자열로 보낸다. 우리 버킷에서 온 것인지 본다. */
    private final ImageUrlValidator imageUrlValidator;

    /**
     * 판매자의 경매방을 생성한다. share_code는 서버가 발급해(충돌 시 재시도) 응답에 담는다 —
     * 공개 화면이 이 방을 지목하는 유일한 식별자다.
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

        imageUrlValidator.validate(request.coverImageUrl());

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        AuctionRoom auctionRoom = saveWithUniqueShareCode(sellerProfile, request);

        // 물품 추가는 이미 만들어진 방에만 할 수 있어서, 생성 직후엔 itemCount가 항상 0이다
        // — 조회 없이 바로 0을 넣는다.
        return AuctionRoomPublicResponseDto.from(auctionRoom, 0L, true, null);
    }

    /**
     * 공유 코드로 경매방 공개 정보를 조회한다. 인증이 필요 없으며, BEFORE를 포함한 모든 상태에서
     * 동일하게 노출한다(상태별 분기 없음).
     *
     * <p>숫자 PK로 들어오는 문은 두지 않는다. auto_increment PK를 공개 URL에 노출하면
     * {@code 1, 2, 3...}을 순서대로 불러 공유 링크 없이 남의 방을 전부 훑을 수 있다.
     * 공유 코드는 16자 랜덤이라 추측으로 도달할 수 없다.
     *
     * <p>{@code isOwner}와 {@code agreedToTerms}만 보는 사람에 따라 달라진다. {@code isOwner}는 화면이
     * 판매자 조작 UI를 띄울지 정하는 값이고, 실제 권한은 조작 API가 다시 검증한다.
     * {@code agreedToTerms}는 참여자에게만 의미가 있어서 게스트와 방 주인에게는 null이다.
     *
     * @param shareCode    조회할 경매방의 공유 코드
     * @param viewerUserId 조회한 회원의 ID. 로그인하지 않았으면 null
     * @return 조회된 경매방
     * @throws ApplicationException 해당 공유 코드의 경매방이 없거나 soft delete 되었을 때(AUCTION_ROOM_NOT_FOUND)
     */
    public AuctionRoomPublicResponseDto getRoomByShareCode(String shareCode, Long viewerUserId) {

        AuctionRoom auctionRoom = findRoomByShareCode(shareCode);
        Long roomId = auctionRoom.getAuctionRoomId();
        boolean isOwner = isOwnedBy(auctionRoom, viewerUserId);

        // 방 주인은 참여자가 아니라 진행자다. 동의 여부를 묻는 대상이 아니므로 게스트와 같이 null이고,
        // 참여 기록도 읽지 않는다. false를 주면 판매자가 자기 방에 들어갈 때마다 동의 화면을 만난다.
        Boolean agreedToTerms = (viewerUserId == null || isOwner) ? null
                : auctionParticipantRepository
                        .existsByAuctionRoom_AuctionRoomIdAndUser_UserIdAndAgreedAtIsNotNull(roomId, viewerUserId);

        return AuctionRoomPublicResponseDto.from(
                auctionRoom,
                countItems(roomId),
                isOwner,
                agreedToTerms);
    }

    /**
     * 로그인한 사용자의 경매방을 최신순으로 조회한다. 내가 만든 방과 내가 참여한 방이 함께 나온다.
     *
     * <p>판매자 프로필을 찾지 않는다. 판매자로 등록한 적 없는 사용자도 참여한 방은 봐야 한다.
     *
     * <p>상태·검색어·역할을 서버가 받는 이유는 커서 페이지네이션이기 때문이다. 화면에서 거르면
     * 받아온 첫 쪽 안에서만 걸려 "있는데 검색에 안 나오는" 목록이 된다.
     *
     * @param userId  조회를 요청한 회원의 ID
     * @param keyword 경매방 이름 부분 일치. null이면 전체
     * @param status  경매방 상태. null이면 전체
     * @param role    SELLER면 내가 만든 방, BUYER면 참여한 남의 방. null이면 전체
     * @param cursor  이전 쪽의 마지막 경매방 ID. null이면 첫 쪽
     * @param size    한 쪽 크기. null이면 기본값
     * @return 경매방 목록 한 쪽
     */
    public CursorPageResponse<AuctionRoomListItemResponseDto> getMyRooms(
            Long userId, String keyword, AuctionRoomStatus status, AuctionRoomRole role,
            Long cursor, Integer size) {

        int pageSize = (size != null) ? size : AuctionRoomRepository.DEFAULT_PAGE_SIZE;

        List<AuctionRoomListItemResponseDto> fetched =
                auctionRoomRepository.search(userId, keyword, status, role, cursor, pageSize);

        boolean hasNext = fetched.size() > pageSize;
        List<AuctionRoomListItemResponseDto> page = hasNext ? fetched.subList(0, pageSize) : fetched;
        List<AuctionRoomListItemResponseDto> content = page.stream()
                .map(this::withLiveParticipantCount)
                .toList();

        Long nextCursor = hasNext ? content.get(content.size() - 1).auctionRoomId() : null;

        return CursorPageResponse.of(content, nextCursor);
    }

    /**
     * 방송 중인 방에만 지금 접속 중인 수를 채운다. {@code RoomSseManager}가 들고 있는 SSE
     * 커넥션 수라 방 안 화면의 "N명 참여 중"과 같은 값이다.
     *
     * <p>{@code auction_participants} 행 수를 세지 않는다. 그건 로그인하고 붙은 사람의 누적이라
     * 같은 방을 목록과 방 안에서 볼 때 숫자가 달라진다.
     *
     * <p>시작 전·종료된 방은 커넥션이 0이라 값이 없다. {@code null}로 두면 카드가 그 줄을 안 그린다.
     */
    private AuctionRoomListItemResponseDto withLiveParticipantCount(AuctionRoomListItemResponseDto item) {
        if (item.status() != AuctionRoomStatus.OPEN) {
            return item;
        }

        return item.withParticipantCount((long) roomSseManager.getParticipantCount(item.auctionRoomId()));
    }

    /** 목록 화면 필터 바의 탭 숫자. 목록과 달리 {@code status}를 안 받는다 — 그게 세는 대상이다. */
    public AuctionRoomCountsResponseDto getMyRoomCounts(Long userId, String keyword, AuctionRoomRole role) {
        return auctionRoomRepository.countByStatus(userId, keyword, (role != null) ? role.name() : null);
    }

    /**
     * 경매방의 물품별 낙찰 결과를 조회한다. 인증이 필요 없으며, 로그인한 요청에만 물품마다
     * 요청자의 최종 순위를 함께 담는다.
     *
     * <p>상태로 거르지 않는다. 방이 아직 열려 있어도 마감된 물품의 결과는 볼 수 있어야 하고,
     * 어느 물품이 아직 진행 중인지는 {@code status}로 드러난다.
     *
     * <p>쿼리는 두 번이다 — 물품과 낙찰자를 한 번, 요청자의 순위를 한 번. 물품마다 후보를
     * 조회하면 물품 수만큼 쿼리가 나간다.
     *
     * @param shareCode 조회할 경매방의 공유 코드
     * @param userId    요청자의 회원 ID. 비로그인이면 {@code null}
     * @throws ApplicationException 해당 공유 코드의 경매방이 없거나 soft delete 되었을 때(AUCTION_ROOM_NOT_FOUND)
     */
    public AuctionRoomResultResponseDto getResults(String shareCode, Long userId) {

        AuctionRoom auctionRoom = findRoomByShareCode(shareCode);
        Long auctionRoomId = auctionRoom.getAuctionRoomId();

        Map<Long, MyCandidateRankProjection> myRanks = findMyRanks(auctionRoomId, userId);

        List<AuctionItemResultResponseDto> items = auctionItemRepository.findResults(auctionRoomId).stream()
                .map(item -> toResult(item, myRanks.get(item.auctionItemId())))
                .toList();

        return AuctionRoomResultResponseDto.of(auctionRoom, items);
    }

    /** 비로그인이면 조회하지 않는다. 순위를 물어볼 사람이 없다. */
    private Map<Long, MyCandidateRankProjection> findMyRanks(Long auctionRoomId, Long userId) {
        if (userId == null) {
            return Map.of();
        }
        return dealCandidateRepository.findMyRanksInRoom(auctionRoomId, userId).stream()
                .collect(Collectors.toMap(MyCandidateRankProjection::auctionItemId, rank -> rank));
    }

    /**
     * 낙찰자와 낙찰가는 {@code SOLD}일 때만 채운다. {@code leaderUser}는 입찰이 들어올 때마다
     * 갱신되므로 진행 중인 물품에도 값이 있는데, 그 사람은 아직 낙찰자가 아니다.
     * 유찰 물품의 {@code currentPrice}가 시작가로 남아 있는 것도 같은 이유로 내리지 않는다.
     */
    private AuctionItemResultResponseDto toResult(
            AuctionItemResultProjection item, MyCandidateRankProjection myRank) {

        boolean sold = item.status() == AuctionItemStatus.SOLD;

        return new AuctionItemResultResponseDto(
                item.auctionItemId(),
                item.productName(),
                item.imageUrl(),
                item.status(),
                sold ? item.currentPrice() : null,
                sold ? item.leaderNickname() : null,
                myRank == null ? null : myRank.candidateRank(),
                myRank == null ? null : myRank.bidAmount());
    }

    /**
     * 소유자 본인의 경매방 설정을 부분 수정한다. 요청에서 생략된(null) 필드는 기존 값을 유지한다.
     *
     * <p>수정 가능 범위는 <b>무엇을 바꾸려 하는지</b>에 따라 다르다.
     * <ul>
     *   <li>이름과 방송 링크만 바꾸는 요청은 경매가 진행 중이어도 통과한다. 둘 다 방송을 켠
     *       뒤에야 잘못이 드러나는 값이라, 그때 못 고치면 고칠 방법이 아예 없다 — 이름은
     *       오타, 방송 링크는 "안 열려요"라는 말을 듣고서야 안다</li>
     *   <li>그 밖의 필드(커버 이미지·소개·Soft Close 설정)를 하나라도 건드리면, 이 방의 물품
     *       중 하나라도 READY가 아닌 상태로 경매에 올라간 적이 있는 순간부터 거절된다.
     *       참여자가 이미 보고 입찰을 판단한 조건이라 진행 중에 바뀌면 안 된다</li>
     *   <li>종료된 방은 이름조차 바꿀 수 없다 — 참여자에게는 결과 기록이라, 나중에 제목이
     *       바뀌면 자기가 참여했던 방을 알아볼 수 없게 된다</li>
     * </ul>
     *
     * @param userId        수정을 요청한 회원의 ID
     * @param auctionRoomId 수정할 경매방의 ID
     * @param request       부분 수정할 경매방 정보
     * @return 수정된 경매방
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               경매방이 없거나 본인 소유가 아닐 때(AUCTION_ROOM_NOT_FOUND),
     *                               경매방이 종료됐을 때(AUCTION_ROOM_CLOSED),
     *                               이름·방송 링크 밖의 필드를 경매가 시작된 뒤에 바꾸려 할 때
     *                               (AUCTION_ROOM_ALREADY_STARTED)
     */
    @Transactional
    public AuctionRoomPublicResponseDto update(Long userId, Long auctionRoomId, AuctionRoomUpdateRequestDto request) {

        // 등록만 막으면 검증이 없는 것과 같다. 수정으로도 URL이 통째로 들어온다.
        imageUrlValidator.validate(request.coverImageUrl());

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        AuctionRoom auctionRoom = findOwnedRoom(sellerProfile, auctionRoomId);

        assertNotClosed(auctionRoom);
        if (request.touchesStartLockedFields()) {
            assertNotStarted(auctionRoomId);
        }

        auctionRoom.update(request);

        /*
         * 이미 방에 들어와 있는 사람들에게 방 정보를 다시 읽으라고 알린다. 이게 없으면
         * 고친 본인 화면만 바뀌고 구매자 화면은 새로고침 전까지 옛 이름을 계속 보여준다.
         */
        domainEventPublisher.publish(RoomUpdated.of(auctionRoomId, LocalDateTime.now()));

        // findOwnedRoom을 통과했으므로 요청자가 곧 소유자다.
        return AuctionRoomPublicResponseDto.from(auctionRoom, countItems(auctionRoomId), true, null);
    }

    /**
     * 보는 사람이 이 방의 주인인지 판정한다. 판매자 프로필을 따로 조회하지 않고 방에 매달린
     * 프로필의 user_id와 바로 비교한다 — 프로필이 없는 평범한 구매자까지 조회 예외로 걸러야
     * 하는 흐름을 피하려는 것이고, LAZY 프록시에서 식별자만 꺼내는 것은 초기화를 일으키지
     * 않아 추가 쿼리도 없다.
     */
    private boolean isOwnedBy(AuctionRoom auctionRoom, Long viewerUserId) {
        return viewerUserId != null
                && viewerUserId.equals(auctionRoom.getSellerProfile().getUser().getUserId());
    }

    private AuctionRoom findRoomByShareCode(String shareCode) {
        return auctionRoomRepository.findByShareCodeAndDeletedAtIsNull(shareCode)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));
    }

    private AuctionRoom findOwnedRoom(SellerProfile sellerProfile, Long auctionRoomId) {
        return auctionRoomRepository
                .findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                        auctionRoomId, sellerProfile.getSellerProfileId())
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));
    }

    private void assertNotClosed(AuctionRoom auctionRoom) {
        if (auctionRoom.getStatus() == AuctionRoomStatus.CLOSED) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_CLOSED);
        }
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
