package com.hot6ix.upbid.domain.product.repository;

import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import java.util.List;
import java.util.Optional;

public interface ProductRepositoryCustom {

    /**
     * 정렬 키를 productId로 고정해 커서를 안정적으로 만든다. 상태는 정렬이 아니라 필터로만
     * 쓴다. {@code limit}은 hasNext 판정을 위해 호출부(요청 size + 1)가 이미 계산해 넘긴다.
     */
    List<ProductSummaryResponseDto> searchByLimit(
            Long sellerProfileId, String keyword, ProductListingStatus status, Long cursor, int limit);

    /**
     * 상세 조회·수정 응답에 쓸 파생 상태. {@link #searchByLimit}과 같은 CASE 식을 재사용해
     * 두 응답이 어긋나지 않게 한다.
     */
    Optional<ProductListingStatus> findListingStatus(Long productId);
}
