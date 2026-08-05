package com.hot6ix.upbid.domain.product.repository;

import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    Optional<Product> findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(Long productId, Long sellerProfileId);

    /**
     * 요청자 소유의 살아있는 상품만 한 번에 조회한다. 물품 벌크 추가에서 상품 수만큼 단건
     * 조회를 돌리지 않으려고 쓴다. <b>없거나 남의 상품인 ID는 결과에서 빠지므로</b>, 호출한
     * 쪽이 요청 목록과 대조해 거절 목록을 만든다.
     */
    List<Product> findByProductIdInAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
            List<Long> productIds, Long sellerProfileId);

    int DEFAULT_PAGE_SIZE = 20;

    /**
     * 정렬 키를 productId로 고정해 커서를 안정적으로 만든다. 상태는 정렬이 아니라 필터로만 쓴다.
     */
    default List<ProductSummaryResponseDto> search(
            Long sellerProfileId, String keyword, ProductListingStatus status, Long cursor, Integer size) {
        int limit = (size != null) ? size : DEFAULT_PAGE_SIZE;
        return searchByLimit(sellerProfileId, keyword, status, cursor, limit + 1);
    }
}
