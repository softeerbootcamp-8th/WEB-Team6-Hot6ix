package com.hot6ix.upbid.domain.upload.controller;

import com.hot6ix.upbid.domain.upload.api.UploadApi;
import com.hot6ix.upbid.domain.upload.dto.request.PresignedUrlRequestDto;
import com.hot6ix.upbid.domain.upload.dto.response.PresignedUrlResponseDto;
import com.hot6ix.upbid.domain.upload.service.UploadService;
import com.hot6ix.upbid.global.response.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController implements UploadApi {

    private final UploadService uploadService;

    // 로그인 검사에 쓸 코드가 없다. WebMvcConfig가 AuthInterceptor를 /api/v1/** 전체에 걸어서
    // @GuestAllowed를 안 붙이면 세션이 없는 요청은 401(code 1005)로 막힌다.
    @PostMapping("/presigned-url")
    @Override
    public ResponseEntity<CommonResponse<PresignedUrlResponseDto>> createPresignedUrl(
            Long userId, PresignedUrlRequestDto request) {

        PresignedUrlResponseDto response = uploadService.createPresignedUrl(userId, request);

        return ResponseEntity.ok(CommonResponse.ok(response, "업로드 URL이 발급되었습니다."));
    }
}
