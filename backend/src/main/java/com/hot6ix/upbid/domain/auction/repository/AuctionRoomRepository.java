package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionRoomRepository extends JpaRepository<AuctionRoom, Long> {

    Optional<AuctionRoom> findByAuctionRoomIdAndDeletedAtIsNull(Long auctionRoomId);

    Optional<AuctionRoom> findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
            Long auctionRoomId, Long sellerProfileId);

    boolean existsByAuctionRoomIdAndDeletedAtIsNull(Long auctionRoomId);
}
