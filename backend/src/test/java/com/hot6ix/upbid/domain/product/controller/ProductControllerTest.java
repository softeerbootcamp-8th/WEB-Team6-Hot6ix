package com.hot6ix.upbid.domain.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.product.dto.request.ProductCreateRequestDto;
import com.hot6ix.upbid.domain.product.dto.request.ProductUpdateRequestDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductResponseDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.domain.product.exception.ProductErrorType;
import com.hot6ix.upbid.domain.product.service.ProductService;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.response.CursorPageResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    private ProductResponseDto sampleResponse() {
        return ProductResponseDto.builder()
                .productId(1L)
                .name("승민의 노트북")
                .description("깨끗합니다")
                .imageUrl("https://cdn.hot6ix.com/product.png")
                .referenceUrl("https://example.com/product")
                .build();
    }

    @Test
    @DisplayName("상품을 등록하면 201과 등록된 정보를 반환한다")
    void create() throws Exception {

        ProductCreateRequestDto request = ProductCreateRequestDto.builder()
                .name("승민의 노트북")
                .description("깨끗합니다")
                .imageUrl("https://cdn.hot6ix.com/product.png")
                .referenceUrl("https://example.com/product")
                .build();

        when(productService.create(eq(1L), any(ProductCreateRequestDto.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/products")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("승민의 노트북"));
    }

    @Test
    @DisplayName("설명·이미지·참고 링크 없이도 상품을 등록할 수 있다")
    void create_withoutOptionalFields() throws Exception {

        ProductCreateRequestDto request = ProductCreateRequestDto.builder()
                .name("승민의 노트북")
                .build();

        when(productService.create(eq(1L), any(ProductCreateRequestDto.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/products")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("상품명이 없으면 등록 시 400을 반환한다")
    void create_blankName() throws Exception {

        ProductCreateRequestDto request = ProductCreateRequestDto.builder()
                .name("")
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("상품명에 금지 문자가 포함되면 등록 시 400을 반환한다")
    void create_invalidName() throws Exception {

        ProductCreateRequestDto request = ProductCreateRequestDto.builder()
                .name("<script>")
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("이미지 URL 형식이 올바르지 않으면 등록 시 400을 반환한다")
    void create_invalidImageUrl() throws Exception {

        ProductCreateRequestDto request = ProductCreateRequestDto.builder()
                .name("승민의 노트북")
                .imageUrl("not-a-url")
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 등록 시 404와 에러코드를 반환한다")
    void create_sellerProfileNotFound() throws Exception {

        ProductCreateRequestDto request = ProductCreateRequestDto.builder()
                .name("승민의 노트북")
                .build();

        when(productService.create(eq(1L), any(ProductCreateRequestDto.class)))
                .thenThrow(new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));

        mockMvc.perform(post("/api/v1/products")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(3002));
    }

    @Test
    @DisplayName("본인 소유 상품을 상세 조회하면 200과 상품 정보를 반환한다")
    void getDetail() throws Exception {

        when(productService.getDetail(eq(1L), eq(1L))).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/products/1").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("승민의 노트북"));
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 상세 조회 시 404와 에러코드를 반환한다")
    void getDetail_sellerProfileNotFound() throws Exception {

        when(productService.getDetail(eq(1L), eq(1L)))
                .thenThrow(new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/products/1").header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(3002));
    }

    @Test
    @DisplayName("상품이 없거나 본인 소유가 아니면 상세 조회 시 404와 에러코드를 반환한다")
    void getDetail_productNotFound() throws Exception {

        when(productService.getDetail(eq(1L), eq(999L)))
                .thenThrow(new ApplicationException(ProductErrorType.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/products/999").header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("상품 목록을 조회하면 200과 목록·다음 커서를 반환한다")
    void getList() throws Exception {

        ProductSummaryResponseDto summary = ProductSummaryResponseDto.builder()
                .productId(2L)
                .name("승민의 노트북")
                .status(ProductListingStatus.UNLISTED)
                .build();

        when(productService.getList(eq(1L), eq("노트북"), eq(ProductListingStatus.UNLISTED), eq(3L), eq(10)))
                .thenReturn(CursorPageResponse.of(List.of(summary), 2L));

        mockMvc.perform(get("/api/v1/products")
                        .header("X-User-Id", "1")
                        .param("keyword", "노트북")
                        .param("status", "UNLISTED")
                        .param("cursor", "3")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("승민의 노트북"))
                .andExpect(jsonPath("$.data.nextCursor").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 목록 조회 시 404와 에러코드를 반환한다")
    void getList_sellerProfileNotFound() throws Exception {

        when(productService.getList(eq(1L), any(), any(), any(), any()))
                .thenThrow(new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/products").header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(3002));
    }

    @Test
    @DisplayName("상품을 수정하면 200과 수정된 정보를 반환한다")
    void update() throws Exception {

        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder()
                .name("새 이름")
                .build();

        when(productService.update(eq(1L), eq(1L), any(ProductUpdateRequestDto.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/products/1")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("승민의 노트북"));
    }

    @Test
    @DisplayName("상품명이 없으면 수정 시 400을 반환한다")
    void update_blankName() throws Exception {

        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder()
                .name("")
                .build();

        mockMvc.perform(put("/api/v1/products/1")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("상품이 없거나 본인 소유가 아니면 수정 시 404와 에러코드를 반환한다")
    void update_productNotFound() throws Exception {

        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder()
                .name("새 이름")
                .build();

        when(productService.update(eq(1L), eq(999L), any(ProductUpdateRequestDto.class)))
                .thenThrow(new ApplicationException(ProductErrorType.PRODUCT_NOT_FOUND));

        mockMvc.perform(put("/api/v1/products/999")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("경매방이 시작된 적 있는 상품은 수정 시 409와 에러코드를 반환한다")
    void update_auctionAlreadyStarted() throws Exception {

        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder()
                .name("새 이름")
                .build();

        when(productService.update(eq(1L), eq(1L), any(ProductUpdateRequestDto.class)))
                .thenThrow(new ApplicationException(ProductErrorType.PRODUCT_AUCTION_ALREADY_STARTED));

        mockMvc.perform(put("/api/v1/products/1")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(5002));
    }

    @Test
    @DisplayName("상품을 삭제한다")
    void deleteProduct() throws Exception {

        mockMvc.perform(delete("/api/v1/products/1").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("상품이 없거나 본인 소유가 아니면 삭제 시 404와 에러코드를 반환한다")
    void delete_productNotFound() throws Exception {

        doThrow(new ApplicationException(ProductErrorType.PRODUCT_NOT_FOUND))
                .when(productService).delete(eq(1L), eq(999L));

        mockMvc.perform(delete("/api/v1/products/999").header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("경매방이 시작된 적 있는 상품은 삭제 시 409와 에러코드를 반환한다")
    void delete_auctionAlreadyStarted() throws Exception {

        doThrow(new ApplicationException(ProductErrorType.PRODUCT_AUCTION_ALREADY_STARTED))
                .when(productService).delete(eq(1L), eq(1L));

        mockMvc.perform(delete("/api/v1/products/1").header("X-User-Id", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(5002));
    }
}
