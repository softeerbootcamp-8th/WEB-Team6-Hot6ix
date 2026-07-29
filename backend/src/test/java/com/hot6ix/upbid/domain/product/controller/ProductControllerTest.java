package com.hot6ix.upbid.domain.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.product.dto.request.ProductCreateRequestDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductResponseDto;
import com.hot6ix.upbid.domain.product.service.ProductService;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
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
}
