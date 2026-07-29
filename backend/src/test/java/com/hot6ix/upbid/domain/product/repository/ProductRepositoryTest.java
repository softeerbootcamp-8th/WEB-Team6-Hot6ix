package com.hot6ix.upbid.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class ProductRepositoryTest extends AbstractMySqlContainerTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private UserRepository userRepository;

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
}
