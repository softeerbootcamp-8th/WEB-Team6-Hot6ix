package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomCountsResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomListItemResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomRole;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionRoomRepository extends JpaRepository<AuctionRoom, Long> {

    Optional<AuctionRoom> findByAuctionRoomIdAndDeletedAtIsNull(Long auctionRoomId);

    Optional<AuctionRoom> findByShareCodeAndDeletedAtIsNull(String shareCode);

    /**
     * 공유 코드로 내부 식별자만 읽는다. 공개 경로가 방을 지목하는 유일한 통로라 호출이 잦은데,
     * 물품 목록·SSE 구독은 방 자체가 아니라 ID만 있으면 되므로 엔티티를 통째로 싣지 않는다.
     */
    @Query("select ar.auctionRoomId from AuctionRoom ar "
            + "where ar.shareCode = :shareCode and ar.deletedAt is null")
    Optional<Long> findIdByShareCode(@Param("shareCode") String shareCode);

    Optional<AuctionRoom> findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
            Long auctionRoomId, Long sellerProfileId);

    boolean existsByAuctionRoomIdAndDeletedAtIsNull(Long auctionRoomId);

    boolean existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
            Long auctionRoomId, Long sellerProfileId);

    /** 판매자 프로필 삭제를 막을지 판정하는 데 쓴다({@code SellerProfileService.delete}). */
    boolean existsBySellerProfile_SellerProfileIdAndStatusAndDeletedAtIsNull(
            Long sellerProfileId, AuctionRoomStatus status);

    /**
     * 경매방 행에 쓰기 락을 걸고 조회한다. 이름은 짧지만 <b>soft delete된 방은 걸러진다</b>.
     * 트랜잭션 안에서만 호출해야 한다.
     *
     * <p>물품 시작이 "방의 진행 중 물품을 세고 → 3개 미만이면 시작"하는 흐름이라, 물품 행만
     * 잠가서는 <b>서로 다른 물품</b>에 대한 동시 요청 두 건이 함께 통과해 4개가 될 수 있다.
     * 같은 방의 시작 요청을 이 락으로 한 줄로 세운다.
     *
     * <p>물품 행 락({@code AuctionItemRepository.findByIdForUpdate})과 함께 쓸 때는 항상
     * <b>물품 → 방</b> 순으로 잡는다. 방을 먼저 잡는 코드를 만들면 데드락이 생긴다.
     *
     * <p>fetch join하지 않는 이유는 MySQL {@code FOR UPDATE}가 조인된 행까지 잠그기 때문이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ar from AuctionRoom ar "
            + "where ar.auctionRoomId = :auctionRoomId and ar.deletedAt is null")
    Optional<AuctionRoom> findByIdForUpdate(@Param("auctionRoomId") Long auctionRoomId);

    /**
     * 물품이 전부 마감됐는데 판매자가 종료하지 않아 방치된 경매방의 ID를 찾는다.
     * {@code AuctionRoomIdleCloseRunner}가 자동 종료할 대상을 고르는 데 쓴다.
     *
     * <p>아직 시작하지 않은 {@code READY} 물품이 남은 방은 <b>대상에서 뺀다.</b> 판매자가
     * 이어서 진행할 수 있는 방이고, 수동 종료도 {@code READY}를 건드리지 않는 것과 같은
     * 이유다. 그래서 {@code READY}만 남겨 둔 방은 자동 종료되지 않고 계속 {@code OPEN}이다.
     *
     * <p>여기서 고른 방을 실제로 닫기 전에 {@code AuctionRoomCloseService.closeIfIdle}이
     * 방 행 락을 잡고 같은 조건을 <b>다시</b> 본다. 조회와 종료 사이에 판매자가 물품을
     * 시작했을 수 있어서, 판정 기준은 이 목록이 아니라 그때의 DB다.
     */
    @Query("select ar.auctionRoomId from AuctionRoom ar "
            + "where ar.status = com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus.OPEN "
            + "  and ar.deletedAt is null "
            + "  and not exists ("
            + "    select 1 from AuctionItem ai "
            + "    where ai.auctionRoom = ar "
            + "      and ai.status in ("
            + "        com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.READY, "
            + "        com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.IN_PROGRESS)) "
            + "  and (select max(ai2.endAt) from AuctionItem ai2 "
            + "       where ai2.auctionRoom = ar) < :idleBefore "
            + "order by ar.auctionRoomId")
    List<Long> findIdleRoomIds(@Param("idleBefore") LocalDateTime idleBefore, Limit limit);

    int DEFAULT_PAGE_SIZE = 20;

    /**
     * 내 경매방을 최신순으로 조회한다. 내가 만든 방과 내가 참여한 방이 함께 나온다.
     * 다음 쪽이 있는지 알아야 해서 {@code size + 1}건을 읽고, 판정과 자르기는 Service가 한다.
     *
     * <p>{@code role}을 {@code String}으로 넘기는 이유는 JPQL에서 {@code :role = 'SELLER'}
     * 처럼 문자열과 비교하기 때문이다. enum 파라미터로는 이 비교가 타입이 안 맞는다.
     */
    default List<AuctionRoomListItemResponseDto> search(
            Long userId, String keyword, AuctionRoomStatus status, AuctionRoomRole role,
            Long cursor, Integer size) {
        int limit = (size != null) ? size : DEFAULT_PAGE_SIZE;
        return searchByLimit(userId, keyword, status, (role != null) ? role.name() : null,
                cursor, Limit.of(limit + 1));
    }

    /**
     * 정렬 키를 {@code auctionRoomId}로 고정해 커서를 안정적으로 만든다. {@code createdAt}은
     * 같은 값이 겹치면 페이지 경계에서 항목이 밀리거나 빠진다.
     *
     * <p>참여 조인을 {@code ap.user.userId = :userId}로 묶는다. 유니크 제약
     * {@code uk_auction_participants_room_user} 때문에 방당 최대 1행이라 {@code count(ai)}가
     * 안 부푼다. 이 조건을 {@code where}로 내리면 {@code left join}이 사실상 inner join이 되어
     * 내가 만든 방이 사라진다.
     *
     * <p>{@code or}로 묶어서 인덱스로 후보를 못 좁힌다. 방 수가 커지면 판매자 갈래와 참여
     * 갈래를 나누고 {@code auction_participants(user_id, auction_room_id)} 인덱스를 붙인다
     * (노트 118 참고).
     *
     * <p>{@code participantCount}는 생성자 표현식에서 아예 빼고 9-인자 생성자를 쓴다 —
     * JPQL {@code new}에 bare {@code null}을 넣으면 타입 추론이 안 된다. 그 값은 DB가 아니라
     * {@code RoomSseManager}가 알고 있어 Service가 뒤에 채운다.
     */
    @Query("select new com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomListItemResponseDto("
            + "  ar.auctionRoomId, ar.shareCode, ar.name, ar.coverImageUrl, ar.status, "
            + "  case when ar.sellerProfile.user.userId = :userId then 'SELLER' else 'BUYER' end, "
            + "  ar.sellerProfile.storeName, ar.createdAt, ar.closedAt, count(ai)) "
            + "from AuctionRoom ar "
            + "left join AuctionItem ai on ai.auctionRoom = ar "
            + "left join AuctionParticipant ap on ap.auctionRoom = ar and ap.user.userId = :userId "
            + "where (ar.sellerProfile.user.userId = :userId or ap.auctionParticipantId is not null) "
            + "  and ar.deletedAt is null "
            + "  and (:keyword is null or ar.name like concat('%', :keyword, '%')) "
            + "  and (:status is null or ar.status = :status) "
            + "  and (:role is null "
            + "       or (:role = 'SELLER' and ar.sellerProfile.user.userId = :userId) "
            + "       or (:role = 'BUYER' and ar.sellerProfile.user.userId <> :userId)) "
            + "  and (:cursor is null or ar.auctionRoomId < :cursor) "
            + "group by ar.auctionRoomId, ar.shareCode, ar.name, ar.coverImageUrl, ar.status, "
            + "         ar.sellerProfile.user.userId, ar.sellerProfile.storeName, "
            + "         ar.createdAt, ar.closedAt "
            + "order by ar.auctionRoomId desc")
    List<AuctionRoomListItemResponseDto> searchByLimit(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("status") AuctionRoomStatus status,
            @Param("role") String role,
            @Param("cursor") Long cursor,
            Limit limit);

    /**
     * 상태별 방 개수를 한 행으로 센다. {@code where}는 목록과 같고 {@code status}만 빠진다 —
     * 그게 세는 대상이다. 그래서 상태 탭을 바꿔도 이 숫자는 안 변한다.
     *
     * <p>{@code group by} 대신 조건부 합을 쓴다. 없는 상태는 행 자체가 안 나와서 Service가
     * 0을 채워 넣어야 하는데, 한 행으로 받으면 그 분기가 없다. 한 건도 없으면 {@code sum}이
     * {@code null}이라 {@code coalesce}로 0을 만든다.
     */
    @Query("select new com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomCountsResponseDto("
            + "  coalesce(sum(case when ar.status = com.hot6ix.upbid.domain.auction.entity"
            + ".AuctionRoomStatus.BEFORE then 1L else 0L end), 0L), "
            + "  coalesce(sum(case when ar.status = com.hot6ix.upbid.domain.auction.entity"
            + ".AuctionRoomStatus.OPEN then 1L else 0L end), 0L), "
            + "  coalesce(sum(case when ar.status = com.hot6ix.upbid.domain.auction.entity"
            + ".AuctionRoomStatus.CLOSED then 1L else 0L end), 0L)) "
            + "from AuctionRoom ar "
            + "left join AuctionParticipant ap on ap.auctionRoom = ar and ap.user.userId = :userId "
            + "where (ar.sellerProfile.user.userId = :userId or ap.auctionParticipantId is not null) "
            + "  and ar.deletedAt is null "
            + "  and (:keyword is null or ar.name like concat('%', :keyword, '%')) "
            + "  and (:role is null "
            + "       or (:role = 'SELLER' and ar.sellerProfile.user.userId = :userId) "
            + "       or (:role = 'BUYER' and ar.sellerProfile.user.userId <> :userId))")
    AuctionRoomCountsResponseDto countByStatus(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("role") String role);
}
