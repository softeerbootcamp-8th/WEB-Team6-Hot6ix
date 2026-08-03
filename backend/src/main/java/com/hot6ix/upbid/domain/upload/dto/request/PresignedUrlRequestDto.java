package com.hot6ix.upbid.domain.upload.dto.request;

import com.hot6ix.upbid.domain.upload.UploadDomain;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record PresignedUrlRequestDto(

        @NotNull(message = "업로드 대상은 필수 값입니다.")
        UploadDomain domain,

        // 여기 보낸 값이 서명에 그대로 묶인다. S3에 PUT할 때 Content-Type 헤더가 이 값과
        // 한 글자라도 다르면 S3가 SignatureDoesNotMatch로 거절한다.
        @NotBlank(message = "content-type은 필수 값입니다.")
        String contentType
) {
}
