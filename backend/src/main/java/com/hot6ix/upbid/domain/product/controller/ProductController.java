package com.hot6ix.upbid.domain.product.controller;

import com.hot6ix.upbid.domain.product.api.ProductApi;
import com.hot6ix.upbid.domain.product.dto.request.ProductCreateRequestDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductResponseDto;
import com.hot6ix.upbid.domain.product.service.ProductService;
import com.hot6ix.upbid.global.response.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController implements ProductApi {

    private final ProductService productService;

    @PostMapping
    @Override
    public ResponseEntity<CommonResponse<ProductResponseDto>> create(
            Long userId, ProductCreateRequestDto request) {

        ProductResponseDto response = productService.create(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.ok(response, "상품이 등록되었습니다."));
    }
}
