package com.hot6ix.upbid.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record SellerProfileCreateRequestDto(

        @NotBlank(message = "가게 이름은 필수 값입니다.")
        @Pattern(
                regexp = "^[^<>\"'\\\\;]{2,30}$",
                message = "가게 이름은 2~30자이며 <, >, \", ', \\, ; 문자를 포함할 수 없습니다."
        )
        String storeName,

        @NotBlank(message = "가게 사진 URL은 필수 값입니다.")
        @URL(message = "가게 사진 URL 형식이 올바르지 않습니다.")
        String storeImageUrl,

        @NotBlank(message = "SNS 링크는 필수 값입니다.")
        @URL(message = "SNS 링크 URL 형식이 올바르지 않습니다.")
        String snsUrl,

        @Pattern(regexp = "^[0-9-]{8,13}$", message = "가게 번호 형식이 올바르지 않습니다.")
        String storePhoneNumber,

        @Size(max = 100, message = "한 줄 소개는 100자 이하여야 합니다.")
        String storeDescription
) {
}
