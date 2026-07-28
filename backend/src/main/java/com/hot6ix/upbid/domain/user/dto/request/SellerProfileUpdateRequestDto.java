package com.hot6ix.upbid.domain.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import org.hibernate.validator.constraints.URL;

@Builder
public record SellerProfileUpdateRequestDto(

        @Pattern(
                regexp = "^[^<>\"'\\\\;]{2,30}$",
                message = "가게 이름은 2~30자이며 <, >, \", ', \\, ; 문자를 포함할 수 없습니다."
        )
        String storeName,

        @URL(message = "가게 사진 URL 형식이 올바르지 않습니다.")
        String storeImageUrl,

        @URL(message = "SNS 링크 URL 형식이 올바르지 않습니다.")
        String snsUrl,

        @Pattern(regexp = "^[0-9-]{8,13}$", message = "가게 번호 형식이 올바르지 않습니다.")
        String storePhoneNumber,

        @Size(max = 100, message = "한 줄 소개는 100자 이하여야 합니다.")
        String storeDescription
) {
}
