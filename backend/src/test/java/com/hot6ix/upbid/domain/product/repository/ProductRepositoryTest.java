package com.hot6ix.upbid.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureTestEntityManager
@Import(JpaConfig.class)
class ProductRepositoryTest extends AbstractMySqlContainerTest {

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

    private Product newProduct(SellerProfile sellerProfile) {
        return Product.builder()
                .sellerProfile(sellerProfile)
                .name("승민의 노트북")
                .description("깨끗합니다")
                .build();
    }

    private Product newProduct(SellerProfile sellerProfile, String name) {
        return productRepository.saveAndFlush(Product.builder()
                .sellerProfile(sellerProfile)
                .name(name)
                .build());
    }

    private AuctionRoom newAuctionRoom(SellerProfile sellerProfile) {
        return entityManager.persist(AuctionRoom.builder()
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
    @DisplayName("name 없이 저장하면 예외가 발생한다")
    void name_notNull_violated() {

        SellerProfile sellerProfile = newSellerProfile("seller1@hot6ix.com");
        Product product = Product.builder()
                .sellerProfile(sellerProfile)
                .build();

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("sellerProfile 없이 저장하면 예외가 발생한다")
    void sellerProfile_notNull_violated() {

        Product product = Product.builder()
                .name("승민의 노트북")
                .build();

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("소유자의 활성 상품을 productId로 조회한다")
    void findByProductIdAndSellerProfile_found() {

        SellerProfile sellerProfile = newSellerProfile("seller2@hot6ix.com");
        Product product = productRepository.saveAndFlush(newProduct(sellerProfile));

        Optional<Product> found = productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                product.getProductId(), sellerProfile.getSellerProfileId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("승민의 노트북");
    }

    @Test
    @DisplayName("soft delete된 상품은 조회되지 않는다")
    void findByProductIdAndSellerProfile_excludesDeleted() {

        SellerProfile sellerProfile = newSellerProfile("seller3@hot6ix.com");
        Product product = productRepository.saveAndFlush(newProduct(sellerProfile));
        product.softDelete(LocalDateTime.now());
        productRepository.flush();

        Optional<Product> found = productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                product.getProductId(), sellerProfile.getSellerProfileId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("다른 판매자의 상품은 조회되지 않는다")
    void findByProductIdAndSellerProfile_excludesOtherOwner() {

        SellerProfile owner = newSellerProfile("seller4@hot6ix.com");
        SellerProfile other = newSellerProfile("seller5@hot6ix.com");
        Product product = productRepository.saveAndFlush(newProduct(owner));

        Optional<Product> found = productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                product.getProductId(), other.getSellerProfileId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("연결된 AuctionItem이 없으면 UNREGISTERED로 조회된다")
    void search_unregistered() {

        SellerProfile sellerProfile = newSellerProfile("seller6@hot6ix.com");
        newProduct(sellerProfile, "미등록상품");

        List<ProductSummaryResponseDto> results =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, null, null);

        assertThat(results).extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.UNREGISTERED);
    }

    @Test
    @DisplayName("AuctionItem 상태에 따라 READY·IN_PROGRESS·ENDED로 매핑된다")
    void search_mapsAuctionItemStatus() {

        SellerProfile sellerProfile = newSellerProfile("seller7@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        Product ready = newProduct(sellerProfile, "대기상품");
        newAuctionItem(room, ready, AuctionItemStatus.READY);

        Product inProgress = newProduct(sellerProfile, "진행상품");
        newAuctionItem(room, inProgress, AuctionItemStatus.IN_PROGRESS);

        Product sold = newProduct(sellerProfile, "낙찰상품");
        newAuctionItem(room, sold, AuctionItemStatus.SOLD);

        Product failed = newProduct(sellerProfile, "유찰상품");
        newAuctionItem(room, failed, AuctionItemStatus.FAILED);
        entityManager.flush();

        List<ProductSummaryResponseDto> results =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, null, null);

        assertThat(results)
                .filteredOn(r -> r.productId().equals(ready.getProductId()))
                .extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.READY);
        assertThat(results)
                .filteredOn(r -> r.productId().equals(inProgress.getProductId()))
                .extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.IN_PROGRESS);
        assertThat(results)
                .filteredOn(r -> r.productId().equals(sold.getProductId()))
                .extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.ENDED);
        assertThat(results)
                .filteredOn(r -> r.productId().equals(failed.getProductId()))
                .extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.ENDED);
    }

    @Test
    @DisplayName("status 필터를 걸면 해당 상태의 상품만 조회된다")
    void search_filtersByStatus() {

        SellerProfile sellerProfile = newSellerProfile("seller8@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        newProduct(sellerProfile, "미등록상품");
        Product ready = newProduct(sellerProfile, "대기상품");
        newAuctionItem(room, ready, AuctionItemStatus.READY);
        entityManager.flush();

        List<ProductSummaryResponseDto> results = productRepository.search(
                sellerProfile.getSellerProfileId(), null, ProductListingStatus.READY, null, null);

        assertThat(results).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(ready.getProductId());
    }

    @Test
    @DisplayName("keyword로 상품명을 검색한다")
    void search_filtersByKeyword() {

        SellerProfile sellerProfile = newSellerProfile("seller9@hot6ix.com");
        Product notebook = newProduct(sellerProfile, "승민의 노트북");
        newProduct(sellerProfile, "키보드");

        List<ProductSummaryResponseDto> results =
                productRepository.search(sellerProfile.getSellerProfileId(), "노트북", null, null, null);

        assertThat(results).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(notebook.getProductId());
    }

    @Test
    @DisplayName("productId 최신순으로 정렬되고, cursor보다 작은 productId만 조회된다")
    void search_ordersByProductIdDescWithCursor() {

        SellerProfile sellerProfile = newSellerProfile("seller10@hot6ix.com");
        Product first = newProduct(sellerProfile, "상품1");
        Product second = newProduct(sellerProfile, "상품2");
        Product third = newProduct(sellerProfile, "상품3");

        List<ProductSummaryResponseDto> all =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, null, null);

        assertThat(all).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(third.getProductId(), second.getProductId(), first.getProductId());

        List<ProductSummaryResponseDto> afterThird = productRepository.search(
                sellerProfile.getSellerProfileId(), null, null, third.getProductId(), null);

        assertThat(afterThird).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(second.getProductId(), first.getProductId());
    }

    @Test
    @DisplayName("size를 지정하면 실제로 size+1건까지만 조회된다")
    void search_limitsBySizePlusOne() {

        SellerProfile sellerProfile = newSellerProfile("seller14@hot6ix.com");
        newProduct(sellerProfile, "상품A");
        newProduct(sellerProfile, "상품B");
        newProduct(sellerProfile, "상품C");

        List<ProductSummaryResponseDto> results =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, null, 1);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("다른 판매자의 상품과 soft delete된 상품은 목록에 포함되지 않는다")
    void search_excludesOtherOwnersAndDeleted() {

        SellerProfile owner = newSellerProfile("seller11@hot6ix.com");
        SellerProfile other = newSellerProfile("seller12@hot6ix.com");

        Product visible = newProduct(owner, "노출상품");
        Product deleted = newProduct(owner, "삭제상품");
        deleted.softDelete(LocalDateTime.now());
        productRepository.flush();
        newProduct(other, "타인상품");

        List<ProductSummaryResponseDto> results =
                productRepository.search(owner.getSellerProfileId(), null, null, null, null);

        assertThat(results).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(visible.getProductId());
    }
}
