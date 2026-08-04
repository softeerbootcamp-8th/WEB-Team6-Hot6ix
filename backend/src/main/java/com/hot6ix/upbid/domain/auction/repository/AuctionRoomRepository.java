package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionRoomRepository extends JpaRepository<AuctionRoom, Long> {

    /**
     * 내 경매방 목록 조회 상한. 화면에 페이지네이션이 없어 전량을 내리므로 여기서만 크기를
     * 막는다. 방이 이보다 많은 회원이 생기면 오래된 것부터 조용히 잘리므로, 그때는 상한을
     * 올릴 게 아니라 화면과 함께 페이지네이션을 도입해야 한다.
     */
    int MAX_MY_ROOM_SIZE = 100;

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

    /**
     * 내가 만든 방. 목록 카드가 가게 이름을 쓰므로 판매자 프로필을 fetch join 한다.
     * to-one 연관이라 행이 늘지 않아 상한과 함께 써도 안전하다.
     */
    @Query("select ar from AuctionRoom ar "
            + "join fetch ar.sellerProfile sp "
            + "where sp.user.userId = :userId "
            + "and ar.deletedAt is null "
            + "order by ar.createdAt desc, ar.auctionRoomId desc")
    List<AuctionRoom> findOwnedRooms(@Param("userId") Long userId, Limit limit);

    default List<AuctionRoom> findOwnedRooms(Long userId) {
        return findOwnedRooms(userId, Limit.of(MAX_MY_ROOM_SIZE));
    }

    /**
     * 내가 입찰한 남의 방. 참여를 입찰로 정의하므로 입장만 한 방은 들어오지 않는다.
     *
     * <p>{@code exists} 로 판정한다. 조인으로 풀면 한 방에 여러 번 입찰한 사람에게 같은 방이
     * 여러 줄 나온다.
     *
     * <p>내 방을 뺀다. 판매자 본인 입찰이 차단돼 있어 겹칠 일이 없지만, 그 규칙이 붙기 전
     * 데이터가 남아 있으면 한 방이 개설방과 참여방으로 두 번 나온다. 조건 하나로 데이터와
     * 무관하게 막고, "참여방은 남의 방"이라는 뜻도 쿼리에 드러난다.
     *
     * <p><b>삭제 필터를 방에만 건다.</b> 지워진 방은 목록에서 뺀다 — 카드가 입장 버튼을 그리는데
     * 들어갈 방이 없다. 반면 판매자가 프로필을 지운 방은 남긴다. 참여 이력은 지나간 사실이라
     * 상대가 나갔다고 내 기록이 없어지면 안 된다.
     */
    @Query("select ar from AuctionRoom ar "
            + "join fetch ar.sellerProfile sp "
            + "where ar.deletedAt is null "
            + "and sp.user.userId <> :userId "
            + "and exists (select b.bidId from Bid b "
            + "             where b.auctionItem.auctionRoom.auctionRoomId = ar.auctionRoomId "
            + "               and b.bidder.userId = :userId) "
            + "order by ar.createdAt desc, ar.auctionRoomId desc")
    List<AuctionRoom> findParticipatedRooms(@Param("userId") Long userId, Limit limit);

    default List<AuctionRoom> findParticipatedRooms(Long userId) {
        return findParticipatedRooms(userId, Limit.of(MAX_MY_ROOM_SIZE));
    }
}
