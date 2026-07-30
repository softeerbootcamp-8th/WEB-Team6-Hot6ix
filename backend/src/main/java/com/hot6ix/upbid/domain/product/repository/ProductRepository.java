package com.hot6ix.upbid.domain.product.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(Long productId, Long sellerProfileId);

    /**
     * 목록 조회 기본 페이지 크기.
     */
    int DEFAULT_PAGE_SIZE = 20;

    /**
     * 판매자 본인 상품 목록을 productId 최신순으로 조회한다. 정렬 키를 항상 불변인
     * productId로 고정해 커서가 안정적으로 동작하게 하고, 파생 상태(등록 여부·경매 상태)는
     * 정렬이 아니라 필터로만 사용한다. 한 상품에 연결된 AuctionItem이 여러 건이어도
     * 가장 최근 것(auctionItemId 최댓값) 하나만 상태 판정에 사용한다.
     *
     * <p>{@code status} 필터는 커스텀 enum({@link ProductListingStatus})을 그대로 바인드하면
     * Hibernate가 파라미터의 값 매핑을 추론하지 못해 예외가 나므로(엔티티에 매핑된 속성이
     * 아니라서), 여기서 매핑된 속성({@code ai.auctionItemId}, {@code ai.status})과 직접
     * 비교 가능한 형태로 미리 변환해 하위 쿼리에 넘긴다.
     */
    default List<ProductSummaryResponseDto> search(
            Long sellerProfileId, String keyword, ProductListingStatus status, Long cursor, Integer size) {
        int limit = (size != null) ? size : DEFAULT_PAGE_SIZE;
        Boolean matchUnregistered = (status == ProductListingStatus.UNREGISTERED) ? Boolean.TRUE : null;
        List<AuctionItemStatus> matchAuctionStatuses = (status == null) ? null : switch (status) {
            case UNREGISTERED -> null;
            case READY -> List.of(AuctionItemStatus.READY);
            case IN_PROGRESS -> List.of(AuctionItemStatus.IN_PROGRESS);
            case ENDED -> List.of(AuctionItemStatus.SOLD, AuctionItemStatus.FAILED);
        };
        return searchByLimit(sellerProfileId, keyword, matchUnregistered, matchAuctionStatuses, cursor,
                Limit.of(limit + 1));
    }

    @Query("select new com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto("
            + "  p.productId, p.name, p.imageUrl, "
            + "  case"
            + "    when ai.auctionItemId is null then com.hot6ix.upbid.domain.product.entity.ProductListingStatus.UNREGISTERED"
            + "    when ai.status = com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.READY"
            + "      then com.hot6ix.upbid.domain.product.entity.ProductListingStatus.READY"
            + "    when ai.status = com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.IN_PROGRESS"
            + "      then com.hot6ix.upbid.domain.product.entity.ProductListingStatus.IN_PROGRESS"
            + "    else com.hot6ix.upbid.domain.product.entity.ProductListingStatus.ENDED"
            + "  end, "
            + "  p.createdAt) "
            + "from Product p "
            + "left join AuctionItem ai on ai.product = p and ai.auctionItemId = ("
            + "  select max(ai2.auctionItemId) from AuctionItem ai2 where ai2.product = p) "
            + "where p.sellerProfile.sellerProfileId = :sellerProfileId "
            + "  and p.deletedAt is null "
            + "  and (:keyword is null or p.name like concat('%', :keyword, '%')) "
            + "  and (:cursor is null or p.productId < :cursor) "
            + "  and (:matchUnregistered is null or ai.auctionItemId is null) "
            + "  and (:matchAuctionStatuses is null or ai.status in :matchAuctionStatuses) "
            + "order by p.productId desc")
    List<ProductSummaryResponseDto> searchByLimit(
            @Param("sellerProfileId") Long sellerProfileId,
            @Param("keyword") String keyword,
            @Param("matchUnregistered") Boolean matchUnregistered,
            @Param("matchAuctionStatuses") List<AuctionItemStatus> matchAuctionStatuses,
            @Param("cursor") Long cursor,
            Limit limit);
}
