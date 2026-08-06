package com.hot6ix.upbid.domain.product.repository;

import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    Optional<Product> findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(Long productId, Long sellerProfileId);

    /**
     * 소유자 확인과 상품 행 쓰기 락을 한 쿼리로 끝낸다. 물품 추가가 "차단 상태인지 보고 →
     * INSERT" 흐름이라, 검사와 삽입 사이에 다른 요청이 끼어들면 같은 상품이 물품 두 개로
     * 동시에 올라갈 수 있다. {@code auction_items.product_id}의 unique 제약을 없앤 뒤로는
     * 이 락이 유일한 방어선이다({@link com.hot6ix.upbid.domain.auction.entity.AuctionItem} javadoc 참고).
     *
     * <p>이 락만으로는 부족하다 — 호출하는 트랜잭션이 {@code READ_COMMITTED}여야 한다.
     * 기본값인 REPEATABLE READ에서는 락을 <b>기다리는 동안</b> 커밋된 물품을 뒤따르는 일반
     * 조회가 보지 못해, 락이 요청을 줄 세워도 낡은 값으로 통과시킨다
     * ({@code AuctionItemService.start}가 겪은 것과 같은 함정이다).
     *
     * <p>fetch join하지 않는다 — MySQL {@code FOR UPDATE}는 조인된 행까지 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId = :productId "
            + "  and p.sellerProfile.sellerProfileId = :sellerProfileId and p.deletedAt is null")
    Optional<Product> findOwnedForUpdate(
            @Param("productId") Long productId, @Param("sellerProfileId") Long sellerProfileId);

    /**
     * {@link #findOwnedForUpdate}의 벌크판. <b>productId 오름차순으로 잠근다</b> — 잠그는
     * 순서가 요청마다 다르면 상품 집합이 겹치는 두 벌크 요청이 서로를 기다리다 데드락에
     * 빠진다. 소유하지 않거나 삭제된 상품은 결과에서 빠지므로, 호출한 쪽이 요청 목록과
     * 대조해 거절 목록을 만든다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId in :productIds "
            + "  and p.sellerProfile.sellerProfileId = :sellerProfileId and p.deletedAt is null "
            + "order by p.productId asc")
    List<Product> findOwnedForUpdate(
            @Param("productIds") Collection<Long> productIds, @Param("sellerProfileId") Long sellerProfileId);

    int DEFAULT_PAGE_SIZE = 20;

    int FIRST_PAGE = 0;

    /**
     * 페이지 번호·크기의 기본값을 채워 {@link #searchByPage}에 넘긴다. 두 값 모두 요청에서
     * 생략할 수 있어 기본값을 한곳에 모은다.
     */
    default Page<ProductSummaryResponseDto> search(
            Long sellerProfileId, String keyword, ProductListingStatus status, Integer page, Integer size) {
        return searchByPage(sellerProfileId, keyword, status, PageRequest.of(
                (page != null) ? page : FIRST_PAGE,
                (size != null) ? size : DEFAULT_PAGE_SIZE));
    }
}
