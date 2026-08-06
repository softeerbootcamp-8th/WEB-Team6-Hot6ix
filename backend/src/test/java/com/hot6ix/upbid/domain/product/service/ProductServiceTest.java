package com.hot6ix.upbid.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.response.CursorPageResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    // 이미지 주소 검증은 ImageUrlValidatorTest에서 본다. 여기서는 통과시킨다.
    @Mock
    private ImageUrlValidator imageUrlValidator;

    @InjectMocks
    private ProductService productService;

    private SellerProfile newSellerProfile() {
        User user = User.builder()
                .email("seller@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build();

        return SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .build();
    }

    private Product newProduct(SellerProfile sellerProfile) {
        return Product.builder()
                .sellerProfile(sellerProfile)
                .name("승민의 노트북")
                .description("깨끗합니다")
                .build();
    }

    @Test
    @DisplayName("판매자 프로필을 가진 회원이 상품을 등록한다")
    void create() {

        SellerProfile sellerProfile = newSellerProfile();
        ProductCreateRequestDto request = ProductCreateRequestDto.builder()
                .name("승민의 노트북")
                .description("깨끗합니다")
                .imageUrl("https://cdn.hot6ix.com/product.png")
                .referenceUrl("https://example.com/product")
                .build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponseDto response = productService.create(1L, request);

        assertThat(response.name()).isEqualTo("승민의 노트북");
        assertThat(response.description()).isEqualTo("깨끗합니다");
        assertThat(response.imageUrl()).isEqualTo("https://cdn.hot6ix.com/product.png");
        assertThat(response.referenceUrl()).isEqualTo("https://example.com/product");
        assertThat(response.status()).isEqualTo(ProductListingStatus.UNREGISTERED);
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 상품 등록 시 예외가 발생한다")
    void create_sellerProfileNotFound() {

        ProductCreateRequestDto request = ProductCreateRequestDto.builder()
                .name("승민의 노트북")
                .build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("본인 소유 상품을 상세 조회한다")
    void getDetail() {

        SellerProfile sellerProfile = newSellerProfile();
        Product product = newProduct(sellerProfile);

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        when(productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(any(), any()))
                .thenReturn(Optional.of(product));
        when(productRepository.findListingStatus(10L))
                .thenReturn(Optional.of(ProductListingStatus.IN_PROGRESS));

        ProductResponseDto response = productService.getDetail(1L, 10L);

        assertThat(response.name()).isEqualTo("승민의 노트북");
        assertThat(response.status()).isEqualTo(ProductListingStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 상세 조회 시 예외가 발생한다")
    void getDetail_sellerProfileNotFound() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getDetail(1L, 10L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);

        verify(productRepository, never())
                .findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(any(), any());
    }

    @Test
    @DisplayName("상품이 없거나 본인 소유가 아니면 상세 조회 시 예외가 발생한다")
    void getDetail_productNotFound() {

        SellerProfile sellerProfile = newSellerProfile();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        when(productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getDetail(1L, 10L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND);
    }

    private ProductSummaryResponseDto newSummary(Long productId) {
        return ProductSummaryResponseDto.builder()
                .productId(productId)
                .name("상품" + productId)
                .status(ProductListingStatus.UNREGISTERED)
                .build();
    }

    @Test
    @DisplayName("요청한 크기만큼 결과가 오면 다음 페이지가 없다")
    void getList_noNextPage() {

        SellerProfile sellerProfile = newSellerProfile();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        when(productRepository.search(any(), any(), any(), any(), eq(2)))
                .thenReturn(List.of(newSummary(3L), newSummary(2L)));

        CursorPageResponse<ProductSummaryResponseDto> response =
                productService.getList(1L, null, null, null, 2);

        assertThat(response.content()).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(3L, 2L);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("요청한 크기보다 하나 더 오면 다음 페이지 커서를 잘라서 반환한다")
    void getList_hasNextPage() {

        SellerProfile sellerProfile = newSellerProfile();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        when(productRepository.search(any(), any(), any(), any(), eq(2)))
                .thenReturn(List.of(newSummary(3L), newSummary(2L), newSummary(1L)));

        CursorPageResponse<ProductSummaryResponseDto> response =
                productService.getList(1L, null, null, null, 2);

        assertThat(response.content()).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(3L, 2L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(2L);
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 목록 조회 시 예외가 발생한다")
    void getList_sellerProfileNotFound() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getList(1L, null, null, null, null))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("READY 상품은 요청 값으로 전체 교체된다")
    void update() {

        SellerProfile sellerProfile = newSellerProfile();
        Product product = newProduct(sellerProfile);
        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder()
                .name("새 이름")
                .description("새 설명")
                .imageUrl("https://cdn.hot6ix.com/new.png")
                .referenceUrl("https://example.com/new")
                .build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        when(productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(any(), any()))
                .thenReturn(Optional.of(product));
        when(productRepository.findListingStatus(10L))
                .thenReturn(Optional.of(ProductListingStatus.READY));

        ProductResponseDto response = productService.update(1L, 10L, request);

        assertThat(response.name()).isEqualTo("새 이름");
        assertThat(response.description()).isEqualTo("새 설명");
        assertThat(response.imageUrl()).isEqualTo("https://cdn.hot6ix.com/new.png");
        assertThat(response.referenceUrl()).isEqualTo("https://example.com/new");
        assertThat(response.status()).isEqualTo(ProductListingStatus.READY);
    }

    @Test
    @DisplayName("이력 없음·유찰·전원 실패(파생 상태 UNREGISTERED) 상품도 수정할 수 있다")
    void update_unregisteredAllowed() {

        SellerProfile sellerProfile = newSellerProfile();
        Product product = newProduct(sellerProfile);
        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder()
                .name("새 이름")
                .build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        when(productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(any(), any()))
                .thenReturn(Optional.of(product));
        when(productRepository.findListingStatus(10L))
                .thenReturn(Optional.of(ProductListingStatus.UNREGISTERED));

        ProductResponseDto response = productService.update(1L, 10L, request);

        assertThat(response.status()).isEqualTo(ProductListingStatus.UNREGISTERED);
        assertThat(product.getName()).isEqualTo("새 이름");
    }

    @ParameterizedTest
    @EnumSource(value = ProductListingStatus.class, names = {"IN_PROGRESS", "ENDED"})
    @DisplayName("진행 중이거나 거래가 살아 있으면(ENDED) 수정 시 예외가 발생한다")
    void update_auctionAlreadyStarted(ProductListingStatus status) {

        SellerProfile sellerProfile = newSellerProfile();
        Product product = newProduct(sellerProfile);
        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder()
                .name("새 이름")
                .build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        when(productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(any(), any()))
                .thenReturn(Optional.of(product));
        when(productRepository.findListingStatus(10L)).thenReturn(Optional.of(status));

        assertThatThrownBy(() -> productService.update(1L, 10L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(ProductErrorType.PRODUCT_AUCTION_ALREADY_STARTED);

        assertThat(product.getName()).isEqualTo("승민의 노트북");
    }

    @Test
    @DisplayName("상품이 없거나 본인 소유가 아니면 수정 시 예외가 발생한다")
    void update_productNotFound() {

        SellerProfile sellerProfile = newSellerProfile();
        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder()
                .name("새 이름")
                .build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        when(productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(1L, 10L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND);

        verify(productRepository, never()).findListingStatus(any());
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 수정 시 예외가 발생한다")
    void update_sellerProfileNotFound() {

        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder()
                .name("새 이름")
                .build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(1L, 10L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("이력 없음·유찰·전원 실패(파생 상태 UNREGISTERED) 상품은 soft delete 된다")
    void delete() {

        SellerProfile sellerProfile = newSellerProfile();
        Product product = newProduct(sellerProfile);

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        when(productRepository.findOwnedForUpdate(eq(10L), any()))
                .thenReturn(Optional.of(product));
        when(productRepository.findListingStatus(10L))
                .thenReturn(Optional.of(ProductListingStatus.UNREGISTERED));

        productService.delete(1L, 10L);

        assertThat(product.isDeleted()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ProductListingStatus.class, names = {"READY", "IN_PROGRESS", "ENDED"})
    @DisplayName("아직 시작 전(READY)이거나 진행 중·거래가 살아 있으면 삭제 시 예외가 발생한다")
    void delete_inAuction(ProductListingStatus status) {

        SellerProfile sellerProfile = newSellerProfile();
        Product product = newProduct(sellerProfile);

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        when(productRepository.findOwnedForUpdate(eq(10L), any()))
                .thenReturn(Optional.of(product));
        when(productRepository.findListingStatus(10L)).thenReturn(Optional.of(status));

        assertThatThrownBy(() -> productService.delete(1L, 10L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(ProductErrorType.PRODUCT_IN_AUCTION);

        assertThat(product.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("상품이 없거나 본인 소유가 아니면 삭제 시 예외가 발생한다")
    void delete_productNotFound() {

        SellerProfile sellerProfile = newSellerProfile();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));
        when(productRepository.findOwnedForUpdate(eq(10L), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(1L, 10L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 삭제 시 예외가 발생한다")
    void delete_sellerProfileNotFound() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(1L, 10L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }
}
