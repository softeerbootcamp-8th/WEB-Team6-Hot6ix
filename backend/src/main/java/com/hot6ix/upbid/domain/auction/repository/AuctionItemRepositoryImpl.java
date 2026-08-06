package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.QAuctionItem;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuctionItemRepositoryImpl implements AuctionItemRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Long> findBlockedProductIdsIn(Collection<Long> productIds) {
        QAuctionItem ai = QAuctionItem.auctionItem;

        return queryFactory
                .select(ai.product.productId)
                .from(ai)
                .where(ai.product.productId.in(productIds),
                        AuctionItemPredicates.blockedForReregistration(ai))
                .fetch();
    }
}
