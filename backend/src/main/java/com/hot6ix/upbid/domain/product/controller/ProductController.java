package com.hot6ix.upbid.domain.product.controller;

import com.hot6ix.upbid.domain.product.api.ProductApi;
import com.hot6ix.upbid.domain.product.dto.request.ProductCreateRequestDto;
import com.hot6ix.upbid.domain.product.dto.request.ProductUpdateRequestDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductResponseDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.domain.product.service.ProductService;
import com.hot6ix.upbid.global.response.CommonResponse;
import com.hot6ix.upbid.global.response.CursorPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController implements ProductApi {

    private final ProductService productService;

    @PostMapping
    @Override
    public ResponseEntity<CommonResponse<ProductResponseDto>> create(Long userId, ProductCreateRequestDto request) {

        ProductResponseDto response = productService.create(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.ok(response, "상품이 등록되었습니다."));
    }

    @GetMapping("/{productId}")
    @Override
    public ResponseEntity<CommonResponse<ProductResponseDto>> getDetail(Long userId, Long productId) {

        ProductResponseDto response = productService.getDetail(userId, productId);

        return ResponseEntity.ok(CommonResponse.ok(response, "상품 상세 조회에 성공했습니다."));
    }

    @GetMapping
    @Override
    public ResponseEntity<CommonResponse<CursorPageResponse<ProductSummaryResponseDto>>> getList(
            Long userId, String keyword, ProductListingStatus status, Long cursor, Integer size) {

        CursorPageResponse<ProductSummaryResponseDto> response = productService.getList(userId, keyword, status, cursor, size);

        return ResponseEntity.ok(CommonResponse.ok(response, "상품 목록 조회에 성공했습니다."));
    }

    @PutMapping("/{productId}")
    @Override
    public ResponseEntity<CommonResponse<ProductResponseDto>> update(
            Long userId, Long productId, ProductUpdateRequestDto request) {

        ProductResponseDto response = productService.update(userId, productId, request);

        return ResponseEntity.ok(CommonResponse.ok(response, "상품이 수정되었습니다."));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ResponseEntity<CommonResponse<Void>> delete(Long userId, Long productId) {

        productService.delete(userId, productId);

        return ResponseEntity.ok(CommonResponse.ok("상품이 삭제되었습니다."));
    }
}
