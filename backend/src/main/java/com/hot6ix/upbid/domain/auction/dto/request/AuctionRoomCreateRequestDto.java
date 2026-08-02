package com.hot6ix.upbid.domain.auction.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import org.hibernate.validator.constraints.URL;

/** 경매방 생성(POST /api/v1/auction-rooms) 요청 바디. */
@Builder
public record AuctionRoomCreateRequestDto(

        @NotBlank(message = "경매방 이름은 필수 값입니다.")
        @Pattern(
                regexp = "^[^<>\"'\\\\;]{1,100}$",
                message = "경매방 이름은 100자 이하이며 <, >, \", ', \\, ; 문자를 포함할 수 없습니다."
        )
        String name,

        @Pattern(regexp = ".*\\S.*", message = "커버 이미지 URL은 빈 값일 수 없습니다.")
        @URL(message = "커버 이미지 URL 형식이 올바르지 않습니다.")
        String coverImageUrl,

        @Pattern(
                regexp = "^[^<>\"'\\\\;]*$",
                message = "경매방 소개는 <, >, \", ', \\, ; 문자를 포함할 수 없습니다."
        )
        String description,

        @Pattern(regexp = ".*\\S.*", message = "라이브 방송 URL은 빈 값일 수 없습니다.")
        @URL(message = "라이브 방송 URL 형식이 올바르지 않습니다.")
        String liveUrl,

        @NotNull(message = "Soft Close 트리거 초는 필수 값입니다.")
        @Min(value = 1, message = "Soft Close 트리거 초는 1초 이상이어야 합니다.")
        @Max(value = 3600, message = "Soft Close 트리거 초는 3600초(1시간) 이하여야 합니다.")
        Integer softCloseTriggerSeconds,

        @NotNull(message = "Soft Close 연장 초는 필수 값입니다.")
        @Min(value = 1, message = "Soft Close 연장 초는 1초 이상이어야 합니다.")
        @Max(value = 3600, message = "Soft Close 연장 초는 3600초(1시간) 이하여야 합니다.")
        Integer softCloseExtendSeconds
) {
}
