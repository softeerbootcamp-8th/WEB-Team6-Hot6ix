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

        @NotNull(message = "입찰 단위는 필수 값입니다.")
        @Min(value = 1, message = "입찰 단위는 1원 이상이어야 합니다.")
        @Max(value = 1_000_000_000_000L, message = "입찰 단위는 1조원 이하여야 합니다.")
        Long bidIncrement,

        @NotNull(message = "Soft Close 트리거 초는 필수 값입니다.")
        @Min(value = 60, message = "Soft Close 트리거 초는 60초(1분) 이상이어야 합니다.")
        @Max(value = 3600, message = "Soft Close 트리거 초는 3600초(1시간) 이하여야 합니다.")
        Integer softCloseTriggerSeconds,

        /**
         * 하한이 60초인 것은 연장 폭이 <b>네트워크 지연 편차보다 충분히 커야</b> 의미가 있기
         * 때문이다. 사람마다 요청이 도착하는 시각이 수십에서 수백 밀리초씩 다른데, 연장이
         * 1초면 그 편차가 승부를 가르는 비중이 그대로 남아 Soft Close를 넣은 이유가 사라진다.
         *
         * <p>값을 60으로 잡은 것은 화면이 <b>분 단위로만</b> 입력받아 최소가 1분이기 때문이다.
         * 화면에서 만들 수 없는 값을 API가 받아주면 두 쪽 규칙이 갈린다.
         */
        @NotNull(message = "Soft Close 연장 초는 필수 값입니다.")
        @Min(value = 60, message = "Soft Close 연장 초는 60초(1분) 이상이어야 합니다.")
        @Max(value = 3600, message = "Soft Close 연장 초는 3600초(1시간) 이하여야 합니다.")
        Integer softCloseExtendSeconds
) {
}
