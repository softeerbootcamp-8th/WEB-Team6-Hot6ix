package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import jakarta.persistence.LockModeType;
import java.util.Optional;
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
}
