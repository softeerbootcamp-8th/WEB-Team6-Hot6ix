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

        /** 빈 문자열은 "지운다"는 뜻이라 통과시킨다({@code @URL}도 빈 값은 검사하지 않는다). */
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
     * 경매가 시작된 뒤에는 못 바꾸는 필드를 이 요청이 건드리는지 본다. 잠그는 기준은
     * <b>참여자가 그 값을 보고 입찰을 판단했는가</b>이다. 시작가 규칙을 좌우하는 Soft Close
     * 설정, 물건을 고르는 근거가 되는 커버 이미지·소개가 여기 해당한다.
     *
     * <p>{@code name}과 {@code liveUrl}은 그 부류가 아니라 열어 둔다.
     * <ul>
     *   <li>이름은 방송 중에 드러난 오타를 고칠 길이 하나는 있어야 한다</li>
     *   <li>방송 링크는 입찰 조건이 아니라 "지금 어디서 보고 있나"에 가깝다. 오히려 링크가
     *       잘못됐다는 것은 방송을 켜고 구매자가 못 들어온 뒤에야 드러나므로, 방송 중에
     *       잠그면 <b>정작 고쳐야 할 순간에 못 고친다</b></li>
     * </ul>
     */
    public boolean touchesStartLockedFields() {
        return coverImageUrl != null
                || description != null
                || softCloseTriggerSeconds != null
                || softCloseExtendSeconds != null;
    }
}
