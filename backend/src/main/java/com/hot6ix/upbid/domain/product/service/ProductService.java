package com.hot6ix.upbid.domain.product.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.product.dto.request.ProductCreateRequestDto;
import com.hot6ix.upbid.domain.product.dto.request.ProductUpdateRequestDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductResponseDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.domain.product.exception.ProductErrorType;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.upload.ImageUrlValidator;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.response.CursorPageResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final ImageUrlValidator imageUrlValidator;

    /**
     * 로그인한 판매자의 상품을 등록한다.
     *
     * @param userId  등록을 요청한 회원의 ID
     * @param request 등록할 상품 정보
     * @return 등록된 상품
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND)
     */
    @Transactional
    public ProductResponseDto create(Long userId, ProductCreateRequestDto request) {

        imageUrlValidator.validate(request.imageUrl());

        SellerProfile sellerProfile = findActiveSellerProfile(userId);

        Product product = Product.from(sellerProfile, request);

        // 방금 만든 상품은 아직 어떤 경매방에도 담기지 않았으므로 파생 상태를 조회할 필요가 없다.
        return ProductResponseDto.from(productRepository.save(product), ProductListingStatus.UNREGISTERED);
    }

    /**
     * 로그인한 판매자 본인 소유의 상품을 상세 조회한다.
     *
     * @param userId    조회를 요청한 회원의 ID
     * @param productId 조회할 상품의 ID
     * @return 조회된 상품
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               상품이 없거나 본인 소유가 아닐 때(PRODUCT_NOT_FOUND)
     */
    public ProductResponseDto getDetail(Long userId, Long productId) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        Product product = findOwnedProduct(sellerProfile, productId);

        return ProductResponseDto.from(product, findListingStatus(productId));
    }

    /**
     * 로그인한 판매자 본인 소유의 상품을 요청 값으로 전체 교체한다.
     * 경매방이 한 번이라도 시작된(READY가 아닌 AuctionItem이 있는) 상품은 수정할 수 없다.
     *
     * @param userId    수정을 요청한 회원의 ID
     * @param productId 수정할 상품의 ID
     * @param request   교체할 상품 정보
     * @return 수정된 상품
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               상품이 없거나 본인 소유가 아닐 때(PRODUCT_NOT_FOUND),
     *                               경매방이 시작된 적 있을 때(PRODUCT_AUCTION_ALREADY_STARTED)
     */
    @Transactional
    public ProductResponseDto update(Long userId, Long productId, ProductUpdateRequestDto request) {

        // 수정은 PUT 전체 교체라 등록과 똑같이 URL이 통째로 들어온다. 등록만 막으면 검증이 없는 것과 같다.
        imageUrlValidator.validate(request.imageUrl());

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        Product product = findOwnedProduct(sellerProfile, productId);
        assertAuctionNotStarted(productId);

        product.update(request);

        return ProductResponseDto.from(product, findListingStatus(productId));
    }

    /**
     * 로그인한 판매자 본인 소유의 상품을 soft delete 한다.
     * 경매방에 물품으로 올라가 있는 상품은 아직 시작 전(READY)이더라도 삭제할 수 없다 —
     * 상품만 지우면 물품 행이 남아 경매방·목록에 삭제된 상품이 계속 노출되기 때문이다.
     * 시작 전이라면 경매방에서 물품을 먼저 빼면 삭제할 수 있다.
     *
     * @param userId    삭제를 요청한 회원의 ID
     * @param productId 삭제할 상품의 ID
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               상품이 없거나 본인 소유가 아닐 때(PRODUCT_NOT_FOUND),
     *                               경매방에 올라가 있을 때(PRODUCT_IN_AUCTION)
     */
    @Transactional
    public void delete(Long userId, Long productId) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        Product product = findOwnedProduct(sellerProfile, productId);
        assertNotInAuction(productId);

        product.softDelete(LocalDateTime.now());
    }

    /**
     * 로그인한 판매자 본인의 상품 목록을 productId 최신순으로 조회한다.
     * 정렬 키를 productId로 고정해 커서가 안정적으로 동작하며, 상태는 정렬이 아니라
     * 필터로만 쓰인다.
     *
     * @param userId  조회를 요청한 회원의 ID
     * @param keyword 상품명 검색어(옵션)
     * @param status  파생 상태 필터(옵션) — UNREGISTERED/READY/IN_PROGRESS/ENDED
     * @param cursor  이전 페이지 마지막 상품의 productId(옵션, 없으면 첫 페이지)
     * @param size    페이지 크기(옵션, 기본 {@value ProductRepository#DEFAULT_PAGE_SIZE})
     * @return 상품 요약 목록과 다음 페이지 커서
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND)
     */
    public CursorPageResponse<ProductSummaryResponseDto> getList(
            Long userId, String keyword, ProductListingStatus status, Long cursor, Integer size) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        int pageSize = (size != null) ? size : ProductRepository.DEFAULT_PAGE_SIZE;

        List<ProductSummaryResponseDto> fetched = productRepository.search(
                sellerProfile.getSellerProfileId(), keyword, status, cursor, pageSize);

        boolean hasNext = fetched.size() > pageSize;
        List<ProductSummaryResponseDto> content = hasNext ? fetched.subList(0, pageSize) : fetched;
        Long nextCursor = hasNext ? content.get(content.size() - 1).productId() : null;

        return CursorPageResponse.of(content, nextCursor);
    }

    private SellerProfile findActiveSellerProfile(Long userId) {
        return sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));
    }

    private Product findOwnedProduct(SellerProfile sellerProfile, Long productId) {
        return productRepository
                .findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(productId, sellerProfile.getSellerProfileId())
                .orElseThrow(() -> new ApplicationException(ProductErrorType.PRODUCT_NOT_FOUND));
    }

    private ProductListingStatus findListingStatus(Long productId) {
        return productRepository.findListingStatus(productId)
                .orElseThrow(() -> new ApplicationException(ProductErrorType.PRODUCT_NOT_FOUND));
    }

    private void assertAuctionNotStarted(Long productId) {
        if (auctionItemRepository.existsByProduct_ProductIdAndStatusNot(productId, AuctionItemStatus.READY)) {
            throw new ApplicationException(ProductErrorType.PRODUCT_AUCTION_ALREADY_STARTED);
        }
    }

    /**
     * 삭제는 수정보다 엄격하다. 수정은 시작 전이면 허용해도 남는 게 없지만, 삭제는 물품 행이
     * 남아 삭제된 상품이 경매방에 계속 노출되므로 READY 물품이어도 막는다.
     */
    private void assertNotInAuction(Long productId) {
        if (auctionItemRepository.existsByProduct_ProductId(productId)) {
            throw new ApplicationException(ProductErrorType.PRODUCT_IN_AUCTION);
        }
    }
}
