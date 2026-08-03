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

    int DEFAULT_PAGE_SIZE = 20;

    /**
     * 상품당 AuctionItem이 여러 건이어도 가장 최근 것(auctionItemId 최댓값) 하나만 쓴다.
     */
    String LATEST_AUCTION_ITEM_JOIN = "left join AuctionItem ai on ai.product = p and ai.auctionItemId = ("
            + "  select max(ai2.auctionItemId) from AuctionItem ai2 where ai2.product = p)";

    /**
     * SOLD·FAILED는 ENDED로 합친다. {@link #LATEST_AUCTION_ITEM_JOIN}의 별칭 {@code ai}에 묶여 있다.
     */
    String DERIVED_STATUS_CASE = "case"
            + "  when ai.auctionItemId is null then com.hot6ix.upbid.domain.product.entity.ProductListingStatus.UNREGISTERED"
            + "  when ai.status = com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.READY"
            + "    then com.hot6ix.upbid.domain.product.entity.ProductListingStatus.READY"
            + "  when ai.status = com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.IN_PROGRESS"
            + "    then com.hot6ix.upbid.domain.product.entity.ProductListingStatus.IN_PROGRESS"
            + "  else com.hot6ix.upbid.domain.product.entity.ProductListingStatus.ENDED"
            + "  end";

    /**
     * 정렬 키를 productId로 고정해 커서를 안정적으로 만든다. 상태는 정렬이 아니라 필터로만 쓴다.
     */
    default List<ProductSummaryResponseDto> search(
            Long sellerProfileId, String keyword, ProductListingStatus status, Long cursor, Integer size) {
        int limit = (size != null) ? size : DEFAULT_PAGE_SIZE;
        return searchByLimit(sellerProfileId, keyword,
                toMatchUnregistered(status), toMatchAuctionStatuses(status),
                cursor, Limit.of(limit + 1));
    }

    // ProductListingStatus를 JPQL 파라미터로 그대로 바인드하면 Hibernate가 값 매핑을 못 찾아
    // 예외가 난다(엔티티 매핑 속성이 아니라서). 그래서 ai.auctionItemId/ai.status와 직접
    // 비교 가능한 형태로 미리 변환한다.
    private Boolean toMatchUnregistered(ProductListingStatus status) {
        return (status == ProductListingStatus.UNREGISTERED) ? Boolean.TRUE : null;
    }

    private List<AuctionItemStatus> toMatchAuctionStatuses(ProductListingStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case UNREGISTERED -> null;
            case READY -> List.of(AuctionItemStatus.READY);
            case IN_PROGRESS -> List.of(AuctionItemStatus.IN_PROGRESS);
            case ENDED -> List.of(AuctionItemStatus.SOLD, AuctionItemStatus.FAILED);
        };
    }

    /**
     * 상세 조회·수정 응답에 쓸 파생 상태. 목록과 같은 CASE 문을 재사용해 두 응답이 어긋나지 않게 한다.
     */
    @Query("select " + DERIVED_STATUS_CASE + " "
            + "from Product p "
            + LATEST_AUCTION_ITEM_JOIN + " "
            + "where p.productId = :productId")
    Optional<ProductListingStatus> findListingStatus(@Param("productId") Long productId);

    @Query("select new com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto("
            + "  p.productId, p.name, p.imageUrl, " + DERIVED_STATUS_CASE + ", p.createdAt) "
            + "from Product p "
            + LATEST_AUCTION_ITEM_JOIN + " "
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
