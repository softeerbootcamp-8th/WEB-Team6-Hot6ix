package com.hot6ix.upbid.domain.auction.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import org.hibernate.validator.constraints.URL;

/** 경매방 설정 부분 수정(PATCH /api/v1/auction-rooms/{roomId}) 요청 바디. 생략한 필드는 기존 값 유지. */
@Builder
public record AuctionRoomUpdateRequestDto(

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

        @Min(value = 1, message = "Soft Close 트리거 초는 1초 이상이어야 합니다.")
        @Max(value = 3600, message = "Soft Close 트리거 초는 3600초(1시간) 이하여야 합니다.")
        Integer softCloseTriggerSeconds,

        @Min(value = 1, message = "Soft Close 연장 초는 1초 이상이어야 합니다.")
        @Max(value = 3600, message = "Soft Close 연장 초는 3600초(1시간) 이하여야 합니다.")
        Integer softCloseExtendSeconds
) {

    /**
     * 경매가 시작된 뒤에는 못 바꾸는 필드를 이 요청이 건드리는지 본다. 이름은 여기 없다 —
     * 방송 중에 드러난 오타를 고칠 길이 하나는 있어야 해서 이름만 예외로 열어 두었다.
     * 나머지는 참여자가 이미 보고 판단한 조건이라 진행 중에 바뀌면 안 된다.
     */
    public boolean touchesStartLockedFields() {
        return coverImageUrl != null
                || description != null
                || liveUrl != null
                || softCloseTriggerSeconds != null
                || softCloseExtendSeconds != null;
    }
}
