package com.hot6ix.upbid.domain.product.repository;

import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.domain.product.entity.ProductSortType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepositoryCustom {

    /**
     * 정렬 키를 productId로 고정하고 방향만 {@code sort}로 고른다. 상태는 정렬이 아니라
     * 필터로만 쓴다.
     *
     * <p>커서가 아니라 offset으로 넘긴다. 화면이 페이지 번호를 눌러 임의 페이지로 바로 가고
     * "총 N개"를 그리므로 커서로는 낼 수 없는 값들이다({@code DealCandidateRepository}가
     * 같은 이유로 offset을 쓴다). 목록이 판매자 본인의 상품이라 크지 않고 남이 끼워 넣지도
     * 않아, offset의 약점인 깊은 페이지 비용과 삽입에 따른 밀림이 드러나지 않는다.
     *
     * <p>전체 개수는 목록과 <b>같은 조건</b>으로 세야 상태 탭별 개수와 목록이 어긋나지 않는다.
     */
    Page<ProductSummaryResponseDto> searchByPage(
            Long sellerProfileId, String keyword, ProductListingStatus status,
            ProductSortType sort, Pageable pageable);

    /**
     * 상세 조회·수정 응답에 쓸 파생 상태. {@link #searchByLimit}과 같은 CASE 식을 재사용해
     * 두 응답이 어긋나지 않게 한다.
     */
    Optional<ProductListingStatus> findListingStatus(Long productId);
}
