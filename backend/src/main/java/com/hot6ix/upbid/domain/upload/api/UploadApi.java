package com.hot6ix.upbid.domain.upload.api;

import com.hot6ix.upbid.domain.upload.dto.request.PresignedUrlRequestDto;
import com.hot6ix.upbid.domain.upload.dto.response.PresignedUrlResponseDto;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "업로드", description = "이미지 업로드용 S3 presigned URL 발급 API")
public interface UploadApi {

    @Operation(
            summary = "이미지 업로드 URL 발급",
            description = "S3에 직접 올릴 수 있는 주소를 발급한다. 브라우저는 서버를 거치지 않고 "
                    + "uploadUrl로 PUT하고, 업로드가 끝나면 fileUrl을 상품의 imageUrl이나 판매자 "
                    + "프로필의 storeImageUrl로 저장한다.\n\n"
                    + "PUT할 때 Content-Type 헤더는 요청에 보낸 contentType과 같아야 한다. 다르면 "
                    + "S3가 403 SignatureDoesNotMatch로 거절한다. Authorization 헤더나 쿠키는 "
                    + "붙이지 않는다. x-amz-acl 헤더도 붙이지 않는다.\n\n"
                    + "uploadUrl은 expiresInSeconds 뒤에 만료된다. 저장할 키 경로는 서버가 정하며 "
                    + "로그인한 회원의 폴더 아래에만 만들어진다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공"),
            @ApiResponse(responseCode = "400", description = "요청 필드 형식 위반 (code 2002) 또는 "
                    + "지원하지 않는 이미지 형식 (code 10001)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)")
    })
    ResponseEntity<CommonResponse<PresignedUrlResponseDto>> createPresignedUrl(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Valid @RequestBody PresignedUrlRequestDto request);
}
