package com.hot6ix.upbid.domain.auction.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * 물품 경매 시작(POST /api/v1/auction-items/{auctionItemId}/start) 요청 바디.
 *
 * <p>상한 12시간은 화면이 고를 수 있는 최댓값과 같다. 화면에서 만들 수 없는 값을 API가
 * 받아주면 두 쪽 규칙이 갈리므로 여기서도 같은 값으로 막는다. 마감 스케줄러의 안전장치
 * 역할도 겸한다 — {@code TaskScheduler}가 지연을 {@code long} 나노초로 들고 있어 약
 * 292년을 넘기면 오버플로하는데, 그보다 훨씬 앞에서 걸린다.
 */
@Builder
public record AuctionItemStartRequestDto(

        @NotNull(message = "경매 시간은 필수 값입니다.")
        @Min(value = 1, message = "경매 시간은 1분 이상이어야 합니다.")
        @Max(value = 720, message = "경매 시간은 720분(12시간) 이하여야 합니다.")
        Integer durationMinutes
) {
}
