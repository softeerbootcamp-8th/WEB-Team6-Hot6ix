package com.hot6ix.upbid.domain.user.controller;

import com.hot6ix.upbid.domain.user.api.SellerProfileApi;
import com.hot6ix.upbid.domain.user.dto.request.SellerProfileCreateRequestDto;
import com.hot6ix.upbid.domain.user.dto.request.SellerProfileUpdateRequestDto;
import com.hot6ix.upbid.domain.user.dto.response.SellerProfileResponseDto;
import com.hot6ix.upbid.domain.user.service.SellerProfileService;
import com.hot6ix.upbid.global.response.CommonResponse;
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
@RequestMapping("/api/v1/seller-profiles")
@RequiredArgsConstructor
public class SellerProfileController implements SellerProfileApi {

    private final SellerProfileService sellerProfileService;

    @PostMapping
    @Override
    public ResponseEntity<CommonResponse<SellerProfileResponseDto>> create(
            Long userId, SellerProfileCreateRequestDto request) {

        SellerProfileResponseDto response = sellerProfileService.create(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.ok(response, "판매자 프로필이 등록되었습니다."));
    }

    @GetMapping("/me")
    @Override
    public ResponseEntity<CommonResponse<SellerProfileResponseDto>> getMyProfile(Long userId) {

        SellerProfileResponseDto response = sellerProfileService.getMyProfile(userId);

        return ResponseEntity.ok(CommonResponse.ok(response, "판매자 프로필 조회에 성공했습니다."));
    }

    @PutMapping("/me")
    @Override
    public ResponseEntity<CommonResponse<SellerProfileResponseDto>> update(
            Long userId, SellerProfileUpdateRequestDto request) {

        SellerProfileResponseDto response = sellerProfileService.update(userId, request);

        return ResponseEntity.ok(CommonResponse.ok(response, "판매자 프로필이 수정되었습니다."));
    }

    @DeleteMapping("/me")
    @Override
    public ResponseEntity<CommonResponse<Void>> delete(Long userId) {

        sellerProfileService.delete(userId);

        return ResponseEntity.ok(CommonResponse.ok("판매자 프로필이 삭제되었습니다."));
    }
}
