package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomListItemResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import jakarta.persistence.LockModeType;
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

    Optional<AuctionRoom> findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
            Long auctionRoomId, Long sellerProfileId);

    boolean existsByAuctionRoomIdAndDeletedAtIsNull(Long auctionRoomId);

    boolean existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
            Long auctionRoomId, Long sellerProfileId);

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

    int DEFAULT_PAGE_SIZE = 20;

    /**
     * 판매자 본인이 만든 경매방을 최신순으로 조회한다. 다음 쪽이 있는지 알아야 해서
     * {@code size + 1}건을 읽고, 판정과 자르기는 Service가 한다(상품 목록과 같은 방식).
     */
    default List<AuctionRoomListItemResponseDto> search(
            Long sellerProfileId, String keyword, AuctionRoomStatus status, Long cursor, Integer size) {
        int limit = (size != null) ? size : DEFAULT_PAGE_SIZE;
        return searchByLimit(sellerProfileId, keyword, status, cursor, Limit.of(limit + 1));
    }

    /**
     * 정렬 키를 {@code auctionRoomId}로 고정해 커서를 안정적으로 만든다. {@code createdAt}은
     * 같은 값이 겹치면 페이지 경계에서 항목이 밀리거나 빠진다.
     *
     * <p>물품 수는 {@code left join} + {@code group by}로 한 쿼리에서 센다. 방마다 count를
     * 돌리면 목록 크기만큼 쿼리가 늘어난다.
     *
     * <p>{@code participantCount}는 생성자 표현식에서 아예 빼고 6-인자 생성자를 쓴다 —
     * JPQL {@code new}에 bare {@code null}을 넣으면 타입 추론이 안 된다.
     */
    @Query("select new com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomListItemResponseDto("
            + "  ar.auctionRoomId, ar.name, ar.coverImageUrl, ar.status, ar.createdAt, count(ai)) "
            + "from AuctionRoom ar "
            + "left join AuctionItem ai on ai.auctionRoom = ar "
            + "where ar.sellerProfile.sellerProfileId = :sellerProfileId "
            + "  and ar.deletedAt is null "
            + "  and (:keyword is null or ar.name like concat('%', :keyword, '%')) "
            + "  and (:status is null or ar.status = :status) "
            + "  and (:cursor is null or ar.auctionRoomId < :cursor) "
            + "group by ar.auctionRoomId, ar.name, ar.coverImageUrl, ar.status, ar.createdAt "
            + "order by ar.auctionRoomId desc")
    List<AuctionRoomListItemResponseDto> searchByLimit(
            @Param("sellerProfileId") Long sellerProfileId,
            @Param("keyword") String keyword,
            @Param("status") AuctionRoomStatus status,
            @Param("cursor") Long cursor,
            Limit limit);
}
