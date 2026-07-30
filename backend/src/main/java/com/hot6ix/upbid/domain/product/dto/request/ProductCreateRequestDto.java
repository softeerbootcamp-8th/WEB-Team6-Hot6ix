package com.hot6ix.upbid.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import org.hibernate.validator.constraints.URL;

@Builder
public record ProductCreateRequestDto(

        @NotBlank(message = "상품명은 필수 값입니다.")
        @Pattern(
                regexp = "^[^<>\"'\\\\;]{1,30}$",
                message = "상품명은 30자 이하이며 <, >, \", ', \\, ; 문자를 포함할 수 없습니다."
        )
        String name,

        @Pattern(
                regexp = "^[^<>\"'\\\\;]{0,100}$",
                message = "상품 설명은 100자 이하이며 <, >, \", ', \\, ; 문자를 포함할 수 없습니다."
        )
        String description,

        @Pattern(regexp = ".*\\S.*", message = "상품 이미지 URL은 빈 값일 수 없습니다.")
        @URL(message = "상품 이미지 URL 형식이 올바르지 않습니다.")
        String imageUrl,

        @Pattern(regexp = ".*\\S.*", message = "참고 링크는 빈 값일 수 없습니다.")
        @URL(message = "참고 링크 URL 형식이 올바르지 않습니다.")
        String referenceUrl
) {
}
