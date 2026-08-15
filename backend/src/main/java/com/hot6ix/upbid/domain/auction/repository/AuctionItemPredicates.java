package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.QAuctionItem;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
import com.hot6ix.upbid.domain.deal.entity.QDealCandidate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * {@code AuctionItem} 하나를 두고 "이 상품을 다시 등록할 수 있는가"를 판정하는 술어다.
 * QProduct·QAuctionItem 조인의 {@code ai} 별칭에 묶여 있다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuctionItemPredicates {

    /**
     * 낙찰됐고 아직 살아 있는 거래가 붙어 있는 물품. 판매자 화면(거래 현황)의 판정을 맞춘다.
     *
     * <p>{@code AuctionItemCloseService.closeIfDue()}가 SOLD를 커밋한 뒤 후보가 실제로
     * INSERT되기까지 수 ms의 창이 있다({@code DealCandidateAwardListener}가
     * {@code AFTER_COMMIT}으로 비동기 처리). 그 사이엔 "SOLD인데 후보 0건"이라 이 식이
     * false를 주고 재등록 가능으로 보인다 — 판매자가 그 순간을 노릴 수 없어 수용한다.
     */
    public static BooleanExpression soldWithLiveDeal(QAuctionItem ai) {
        QDealCandidate dc = QDealCandidate.dealCandidate;

        return ai.status.eq(AuctionItemStatus.SOLD).and(
                JPAExpressions.selectOne()
                        .from(dc)
                        .where(dc.auctionItem.auctionItemId.eq(ai.auctionItemId)
                                .and(dc.status.eq(DealCandidateStatus.COMPLETED)
                                        .or(dc.status.eq(DealCandidateStatus.WAITING)
                                                .and(dc.bidder.deletedAt.isNull()))))
                        .exists());
    }

    /**
     * 이 물품이 있으면 그 상품을 새 물품으로 올릴 수 없다. 유찰(FAILED)과 "낙찰 후 전원
     * 실패"만 빠진다 — 둘 다 판매자가 다시 팔아야 하는 상태다.
     */
    public static BooleanExpression blockedForReregistration(QAuctionItem ai) {
        return ai.status.eq(AuctionItemStatus.READY)
                .or(ai.status.eq(AuctionItemStatus.IN_PROGRESS))
                .or(soldWithLiveDeal(ai));
    }
}
