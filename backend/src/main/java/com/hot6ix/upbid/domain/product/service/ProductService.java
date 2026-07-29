package com.hot6ix.upbid.domain.product.service;

import com.hot6ix.upbid.domain.product.dto.request.ProductCreateRequestDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductResponseDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.domain.product.exception.ProductErrorType;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.response.CursorPageResponse;
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

        SellerProfile sellerProfile = findActiveSellerProfile(userId);

        Product product = Product.from(sellerProfile, request);

        return ProductResponseDto.from(productRepository.save(product));
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

        Product product = productRepository
                .findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                        productId, sellerProfile.getSellerProfileId())
                .orElseThrow(() -> new ApplicationException(ProductErrorType.PRODUCT_NOT_FOUND));

        return ProductResponseDto.from(product);
    }

    /**
     * 로그인한 판매자 본인의 상품 목록을 productId 최신순으로 조회한다.
     * 정렬 키를 productId로 고정해 커서가 안정적으로 동작하며, 상태는 정렬이 아니라
     * 필터로만 쓰인다.
     *
     * @param userId  조회를 요청한 회원의 ID
     * @param keyword 상품명 검색어(옵션)
     * @param status  파생 상태 필터(옵션) — UNLISTED/READY/IN_PROGRESS/ENDED
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
}
