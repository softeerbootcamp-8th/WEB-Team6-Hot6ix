package com.hot6ix.upbid.domain.product.repository;

import com.hot6ix.upbid.domain.product.entity.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(Long productId, Long sellerProfileId);
}
