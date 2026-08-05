package com.hot6ix.upbid.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.QAuctionItem;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.domain.product.entity.QProduct;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * #124가 지적한 JPQL 우회를 QueryDSL로 옮기면 어떤 모습인지 보여주는 견본이다.
 * {@link ProductRepository}는 건드리지 않는다 — 실제 전환은 다음 PR에서 한다.
 *
 * <p>{@link ProductRepository#searchByLimit}의 {@code LATEST_AUCTION_ITEM_JOIN}
 * (상품당 최근 AuctionItem 하나만 쓰는 서브쿼리)은 견본 범위를 넘어서므로 다루지 않는다.
 * 여기서는 상품당 AuctionItem이 최대 1건인 경우만 다룬다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureTestEntityManager
@Import(JpaConfig.class)
class ProductQuerydslExampleTest extends AbstractMySqlContainerTest {

    @Autowired
    private JPAQueryFactory queryFactory;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private SellerProfile newSellerProfile(String email) {
        User user = userRepository.saveAndFlush(User.builder()
                .email(email)
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build());

        return sellerProfileRepository.saveAndFlush(SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .build());
    }

    private Product newProduct(SellerProfile sellerProfile, String name) {
        return productRepository.saveAndFlush(Product.builder()
                .sellerProfile(sellerProfile)
                .name(name)
                .build());
    }

    private AuctionRoom newAuctionRoom(SellerProfile sellerProfile) {
        return entityManager.persist(AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name("승민상점 경매방")
                .build());
    }

    private void newAuctionItem(AuctionRoom auctionRoom, Product product, AuctionItemStatus status) {
        entityManager.persist(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(product)
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(status)
                .build());
    }

    @Test
    @DisplayName("[견본] keyword·cursor가 null이면 BooleanBuilder가 그 조건을 건너뛴다")
    void dynamicSearch_skipsNullConditions() {

        SellerProfile sellerProfile = newSellerProfile("querydsl1@hot6ix.com");
        Product notebook = newProduct(sellerProfile, "승민의 노트북");
        Product keyboard = newProduct(sellerProfile, "키보드");

        List<Product> all = searchByKeywordAndCursor(sellerProfile.getSellerProfileId(), null, null);
        assertThat(all).extracting(Product::getProductId)
                .containsExactly(keyboard.getProductId(), notebook.getProductId());

        List<Product> filteredByKeyword = searchByKeywordAndCursor(sellerProfile.getSellerProfileId(), "노트북", null);
        assertThat(filteredByKeyword).extracting(Product::getProductId)
                .containsExactly(notebook.getProductId());

        List<Product> afterCursor = searchByKeywordAndCursor(
                sellerProfile.getSellerProfileId(), null, keyboard.getProductId());
        assertThat(afterCursor).extracting(Product::getProductId)
                .containsExactly(notebook.getProductId());
    }

    /**
     * {@code ProductRepository.searchByLimit}의
     * {@code "(:keyword is null or ...) and (:cursor is null or ...)"}를 BooleanBuilder로
     * 옮기면 이런 모습이다. null인 조건은 아예 생성된 SQL에서 빠진다 — 항상 참인 or 절이
     * 남지 않는다.
     */
    private List<Product> searchByKeywordAndCursor(Long sellerProfileId, String keyword, Long cursor) {
        QProduct product = QProduct.product;

        BooleanBuilder condition = new BooleanBuilder()
                .and(product.sellerProfile.sellerProfileId.eq(sellerProfileId))
                .and(product.deletedAt.isNull());

        if (keyword != null) {
            condition.and(product.name.contains(keyword));
        }
        if (cursor != null) {
            condition.and(product.productId.lt(cursor));
        }

        return queryFactory
                .selectFrom(product)
                .where(condition)
                .orderBy(product.productId.desc())
                .fetch();
    }

    @Test
    @DisplayName("[견본] 파생 상태 enum을 Boolean·List로 변환하지 않고 그대로 조건을 만든다")
    void dynamicSearch_bindsDerivedStatusWithoutConversion() {

        SellerProfile sellerProfile = newSellerProfile("querydsl2@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        Product unregistered = newProduct(sellerProfile, "미등록상품");

        Product ready = newProduct(sellerProfile, "대기상품");
        newAuctionItem(room, ready, AuctionItemStatus.READY);

        Product sold = newProduct(sellerProfile, "낙찰상품");
        newAuctionItem(room, sold, AuctionItemStatus.SOLD);
        entityManager.flush();

        assertThat(searchByStatus(sellerProfile.getSellerProfileId(), ProductListingStatus.UNREGISTERED))
                .extracting(Product::getProductId)
                .containsExactly(unregistered.getProductId());
        assertThat(searchByStatus(sellerProfile.getSellerProfileId(), ProductListingStatus.READY))
                .extracting(Product::getProductId)
                .containsExactly(ready.getProductId());
        assertThat(searchByStatus(sellerProfile.getSellerProfileId(), ProductListingStatus.ENDED))
                .extracting(Product::getProductId)
                .containsExactly(sold.getProductId());
        assertThat(searchByStatus(sellerProfile.getSellerProfileId(), null))
                .extracting(Product::getProductId)
                .containsExactly(sold.getProductId(), ready.getProductId(), unregistered.getProductId());
    }

    /**
     * {@code ProductRepository.toMatchUnregistered}·{@code toMatchAuctionStatuses}는
     * {@code ProductListingStatus}를 그대로 바인드할 수 없어 Boolean·{@code List<AuctionItemStatus>}로
     * 미리 변환해 넘긴다. QueryDSL에서는 분기마다 그 자체로 타입 안전한 {@code BooleanExpression}을
     * 만들면 되므로 그런 변환이 필요 없다. {@code where(Predicate...)}는 null 원소를 조용히
     * 건너뛰므로({@code DefaultQueryMetadata.addWhere}가 null을 무시) status가 null이어도 안전하다.
     */
    private List<Product> searchByStatus(Long sellerProfileId, ProductListingStatus status) {
        QProduct product = QProduct.product;
        QAuctionItem auctionItem = QAuctionItem.auctionItem;

        return queryFactory
                .selectFrom(product)
                .leftJoin(auctionItem).on(auctionItem.product.eq(product))
                .where(
                        product.sellerProfile.sellerProfileId.eq(sellerProfileId),
                        product.deletedAt.isNull(),
                        toStatusCondition(auctionItem, status))
                .orderBy(product.productId.desc())
                .fetch();
    }

    private BooleanExpression toStatusCondition(QAuctionItem auctionItem, ProductListingStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case UNREGISTERED -> auctionItem.auctionItemId.isNull();
            case READY -> auctionItem.status.eq(AuctionItemStatus.READY);
            case IN_PROGRESS -> auctionItem.status.eq(AuctionItemStatus.IN_PROGRESS);
            case ENDED -> auctionItem.status.in(AuctionItemStatus.SOLD, AuctionItemStatus.FAILED);
        };
    }
}
