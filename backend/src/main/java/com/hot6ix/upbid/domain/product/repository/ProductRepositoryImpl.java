package com.hot6ix.upbid.domain.product.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.QAuctionItem;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemPredicates;
import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.domain.product.entity.QProduct;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ProductSummaryResponseDto> searchByLimit(
            Long sellerProfileId, String keyword, ProductListingStatus status, Long cursor, int limit) {

        QProduct product = QProduct.product;
        QAuctionItem ai = QAuctionItem.auctionItem;
        StringExpression derivedStatusName = derivedStatusName(ai);

        List<Tuple> rows = queryFactory
                .select(product.productId, product.name, product.imageUrl,
                        derivedStatusName, product.createdAt)
                .from(product)
                .leftJoin(ai).on(latestAuctionItem(product, ai))
                .where(
                        product.sellerProfile.sellerProfileId.eq(sellerProfileId),
                        product.deletedAt.isNull(),
                        keywordCondition(product, keyword),
                        cursorCondition(product, cursor),
                        statusCondition(ai, status))
                .orderBy(product.productId.desc())
                .limit(limit)
                .fetch();

        return rows.stream()
                .map(row -> new ProductSummaryResponseDto(
                        row.get(product.productId), row.get(product.name), row.get(product.imageUrl),
                        ProductListingStatus.valueOf(row.get(derivedStatusName)), row.get(product.createdAt)))
                .toList();
    }

    @Override
    public Optional<ProductListingStatus> findListingStatus(Long productId) {
        QProduct product = QProduct.product;
        QAuctionItem ai = QAuctionItem.auctionItem;

        String found = queryFactory
                .select(derivedStatusName(ai))
                .from(product)
                .leftJoin(ai).on(latestAuctionItem(product, ai))
                .where(product.productId.eq(productId))
                .fetchOne();

        return Optional.ofNullable(found).map(ProductListingStatus::valueOf);
    }

    /**
     * 상품당 AuctionItem이 여러 건이어도 가장 최근 것(auctionItemId 최댓값) 하나만 쓴다.
     * 재등록을 허용하면서 상품:물품이 1:N이 됐으므로, 화면에 보이는 파생 상태는 이 "최신"
     * 기준이어야 한다 — 사용자에게 보이는 건 지금 상태 하나다. 재등록 가능 <b>판정</b>은
     * 물품 전체를 보는 {@code AuctionItemRepository.findBlockedProductIdsIn}과 다르며,
     * 이 비대칭은 의도된 것이다(도달 가능한 상태에서는 결과가 같다).
     */
    private BooleanExpression latestAuctionItem(QProduct product, QAuctionItem ai) {
        QAuctionItem latest = new QAuctionItem("latest");

        return ai.product.eq(product).and(ai.auctionItemId.eq(
                JPAExpressions.select(latest.auctionItemId.max())
                        .from(latest)
                        .where(latest.product.eq(product))));
    }

    /**
     * SOLD는 "거래가 살아 있을 때만" ENDED다. 유찰과 "낙찰 후 전원 실패"는 판매자가 다시
     * 팔아야 하는 상태라 UNREGISTERED로 되돌린다 — 그래야 등록 화면의 미등록 목록과
     * 등록 API의 허용 조건이 어긋나지 않는다.
     *
     * <p>결과를 {@code ProductListingStatus}가 아니라 그 이름(String)으로 받는다.
     * {@code ProductListingStatus}는 어떤 엔티티 속성에도 매핑돼 있지 않은 순수 자바
     * enum이라, CASE-WHEN-THEN에 enum 상수를 그대로 넘기면 Hibernate가 반환 타입을 enum으로
     * 되돌리지 못하고 문자열로 내려준다({@code ClassCastException}/리플렉션 인자 불일치로
     * 드러난다). 이름으로 받아 호출부에서 {@code ProductListingStatus.valueOf}로 바꾼다.
     */
    private StringExpression derivedStatusName(QAuctionItem ai) {
        return new CaseBuilder()
                .when(ai.auctionItemId.isNull()).then(ProductListingStatus.UNREGISTERED.name())
                .when(ai.status.eq(AuctionItemStatus.READY)).then(ProductListingStatus.READY.name())
                .when(ai.status.eq(AuctionItemStatus.IN_PROGRESS)).then(ProductListingStatus.IN_PROGRESS.name())
                .when(AuctionItemPredicates.soldWithLiveDeal(ai)).then(ProductListingStatus.ENDED.name())
                .otherwise(ProductListingStatus.UNREGISTERED.name());
    }

    private BooleanExpression keywordCondition(QProduct product, String keyword) {
        return (keyword != null) ? product.name.contains(keyword) : null;
    }

    private BooleanExpression cursorCondition(QProduct product, Long cursor) {
        return (cursor != null) ? product.productId.lt(cursor) : null;
    }

    /**
     * {@code ai}가 없는 행(left join의 null쪽)에서 {@code blockedForReregistration}은
     * FALSE가 아니라 UNKNOWN이고, {@code not UNKNOWN}도 UNKNOWN이라 WHERE에서 행이 통째로
     * 탈락한다. 반드시 null 갈래를 앞에 둬 단락시킨다. ENDED는 {@code soldWithLiveDeal}이
     * null 행에서 이미 FALSE로 떨어지므로 not 없이 그대로 쓴다.
     */
    private BooleanExpression statusCondition(QAuctionItem ai, ProductListingStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case UNREGISTERED -> ai.auctionItemId.isNull()
                    .or(AuctionItemPredicates.blockedForReregistration(ai).not());
            case READY -> ai.status.eq(AuctionItemStatus.READY);
            case IN_PROGRESS -> ai.status.eq(AuctionItemStatus.IN_PROGRESS);
            case ENDED -> AuctionItemPredicates.soldWithLiveDeal(ai);
        };
    }
}
