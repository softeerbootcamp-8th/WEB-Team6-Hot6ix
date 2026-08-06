package com.hot6ix.upbid.domain.product.service;

import com.hot6ix.upbid.domain.product.dto.request.ProductCreateRequestDto;
import com.hot6ix.upbid.domain.product.dto.request.ProductUpdateRequestDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductResponseDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.domain.product.entity.ProductSortType;
import com.hot6ix.upbid.domain.product.exception.ProductErrorType;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.upload.ImageUrlValidator;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final SellerProfileRepository sellerProfileRepository;
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
     * 로그인한 판매자 본인 소유의 상품을 요청 값으로 전체 교체한다. 수정 가능 = 파생 상태가
     * UNREGISTERED(이력 없음·유찰·전원 실패) 또는 READY다 — 진행 중이거나 낙찰돼 거래가 살아
     * 있는 상품은 수정할 수 없다({@link #assertEditable}).
     *
     * <p>유찰·전원 실패 상품(재등록 가능)의 수정은 허용하지만, 결과·거래 조회는 전부
     * {@code products}를 라이브 조인한다({@code AuctionItemRepository.findResults} 등).
     * 그래서 이름·이미지를 바꾸면 그 방의 결과 화면·거래 내역·거래 현황에 소급 반영된다 —
     * 스냅샷 컬럼이 없어서다. 알려진 트레이드오프로 수용한다.
     *
     * @param userId    수정을 요청한 회원의 ID
     * @param productId 수정할 상품의 ID
     * @param request   교체할 상품 정보
     * @return 수정된 상품
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               상품이 없거나 본인 소유가 아닐 때(PRODUCT_NOT_FOUND),
     *                               진행 중이거나 거래가 살아 있을 때(PRODUCT_AUCTION_ALREADY_STARTED)
     */
    @Transactional
    public ProductResponseDto update(Long userId, Long productId, ProductUpdateRequestDto request) {

        // 수정은 PUT 전체 교체라 등록과 똑같이 URL이 통째로 들어온다. 등록만 막으면 검증이 없는 것과 같다.
        imageUrlValidator.validate(request.imageUrl());

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        Product product = findOwnedProduct(sellerProfile, productId);
        ProductListingStatus status = findListingStatus(productId);
        assertEditable(status);

        product.update(request);

        return ProductResponseDto.from(product, status);
    }

    /**
     * 로그인한 판매자 본인 소유의 상품을 soft delete 한다. 삭제 가능 = 파생 상태가
     * UNREGISTERED뿐이다({@link #assertDeletable}) — READY·IN_PROGRESS 물품이 걸려 있으면
     * 상품만 지워도 물품 행이 남아 경매방·목록에 삭제된 상품이 계속 노출된다. 유찰·전원
     * 실패 물품(재등록 가능)은 막지 않는다 — 그 물품 행은 노출이 아니라 기록이고,
     * 결과·거래 조회는 {@code products.deleted_at}을 보지 않으므로 삭제해도 내역이 사라지지
     * 않는다.
     *
     * <p>상품 행에 쓰기 락을 걸고 읽는다({@link ProductRepository#findOwnedForUpdate}).
     * 락이 없으면 삭제가 파생 상태를 확인하는 동안 {@code AuctionItemService.add}가 같은
     * 상품으로 물품을 새로 추가해, 삭제된 상품이 READY 물품으로 경매방에 뜰 수 있다.
     * {@code READ_COMMITTED}가 필요한 이유는 {@code AuctionItemService.start}의 같은
     * 문단을 본다 — 락을 기다리는 동안 커밋된 물품을 놓치지 않아야 한다.
     *
     * @param userId    삭제를 요청한 회원의 ID
     * @param productId 삭제할 상품의 ID
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               상품이 없거나 본인 소유가 아닐 때(PRODUCT_NOT_FOUND),
     *                               경매방에 올라가 있을 때(PRODUCT_IN_AUCTION)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long userId, Long productId) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);
        Product product = findOwnedProductForUpdate(sellerProfile, productId);
        assertDeletable(findListingStatus(productId));

        product.softDelete(LocalDateTime.now());
    }

    /**
     * 로그인한 판매자 본인의 상품 목록을 한 페이지 조회한다. 정렬은 등록 순서(productId)의
     * 방향만 고르고, 상태는 정렬이 아니라 필터로만 쓰인다.
     *
     * <p>화면이 페이지 번호를 눌러 임의 페이지로 이동하고 "총 N개"를 그리므로 커서가 아니라
     * offset 페이지네이션이다. 요청 범위를 넘는 page는 오류가 아니라 빈 목록으로 답한다 —
     * 목록을 보는 사이 상품이 지워져 페이지 수가 줄면 정상적으로도 일어난다.
     *
     * @param userId  조회를 요청한 회원의 ID
     * @param keyword 상품명 검색어(옵션)
     * @param status  파생 상태 필터(옵션) — UNREGISTERED/READY/IN_PROGRESS/ENDED
     * @param sort    정렬 기준(옵션, 기본 최신순) — LATEST/OLDEST
     * @param page    0부터 세는 페이지 번호(옵션, 기본 {@value ProductRepository#FIRST_PAGE})
     * @param size    페이지 크기(옵션, 기본 {@value ProductRepository#DEFAULT_PAGE_SIZE})
     * @return 상품 요약 한 페이지와 전체 개수·전체 페이지 수
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND)
     */
    public PageResponse<ProductSummaryResponseDto> getList(
            Long userId, String keyword, ProductListingStatus status,
            ProductSortType sort, Integer page, Integer size) {

        SellerProfile sellerProfile = findActiveSellerProfile(userId);

        return PageResponse.of(productRepository.search(
                sellerProfile.getSellerProfileId(), keyword, status, sort, page, size));
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

    /**
     * 소유자 확인과 상품 행 쓰기 락을 한 쿼리로 끝낸다. 삭제가 "파생 상태를 보고 →
     * soft delete" 흐름이라, 락 없이는 그 사이에 물품이 추가될 수 있다.
     */
    private Product findOwnedProductForUpdate(SellerProfile sellerProfile, Long productId) {
        return productRepository
                .findOwnedForUpdate(productId, sellerProfile.getSellerProfileId())
                .orElseThrow(() -> new ApplicationException(ProductErrorType.PRODUCT_NOT_FOUND));
    }

    private ProductListingStatus findListingStatus(Long productId) {
        return productRepository.findListingStatus(productId)
                .orElseThrow(() -> new ApplicationException(ProductErrorType.PRODUCT_NOT_FOUND));
    }

    /**
     * 수정 가능 = 파생 상태가 UNREGISTERED(이력 없음·유찰·전원 실패) 또는 READY.
     * 프론트가 같은 규칙으로 버튼을 그린다({@code product-status.ts canEditProduct}) —
     * 파생 상태만 바뀌면 두 쪽이 구조적으로 어긋날 수 없다.
     */
    private void assertEditable(ProductListingStatus status) {
        if (status != ProductListingStatus.UNREGISTERED && status != ProductListingStatus.READY) {
            throw new ApplicationException(ProductErrorType.PRODUCT_AUCTION_ALREADY_STARTED);
        }
    }

    /**
     * 삭제 가능 = 파생 상태가 UNREGISTERED뿐이다. 수정보다 엄격한 것은 그대로다 — READY
     * 물품이 남아 있으면 삭제된 상품이 진행 예정 목록에 계속 뜬다.
     *
     * <p>유찰·전원 실패 물품(파생 상태도 UNREGISTERED로 되돌아간 상태)은 막지 않는다.
     * 그 물품 행은 노출이 아니라 기록이고, 결과·거래 내역·거래 현황 조회
     * (예: {@code AuctionItemRepository.findResults}, {@code DealRepository.findDeals})는
     * 전부 {@code products.deleted_at}을 보지 않으므로 삭제해도 기록에서 사라지지 않는다.
     */
    private void assertDeletable(ProductListingStatus status) {
        if (status != ProductListingStatus.UNREGISTERED) {
            throw new ApplicationException(ProductErrorType.PRODUCT_IN_AUCTION);
        }
    }
}
