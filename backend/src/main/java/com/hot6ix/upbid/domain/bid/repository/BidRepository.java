package com.hot6ix.upbid.domain.bid.repository;

import com.hot6ix.upbid.domain.bid.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid, Long> {
}
