package com.hot6ix.upbid.domain.auction.repository;

import java.util.Collection;
import java.util.List;

public interface AuctionItemRepositoryCustom {

    List<Long> findBlockedProductIdsIn(Collection<Long> productIds);
}
