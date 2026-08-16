package com.hot6ix.upbid.domain.auction.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Builder;

/**
 * 마감 앞당기기(POST /api/v1/auction-items/{auctionItemId}/close-early) 요청 바디.
 *
 * <p><b>바디 자체가 선택이다.</b> 안 보내거나 {@code remainingSeconds}를 비우면 경매방의 Soft
 * Close 트리거 초만큼 남긴다 — 이 API 가 처음부터 하던 동작이라, 이미 붙어 있는 화면이 그대로
 * 돌아간다.
 *
 * <p>절대 시각이 아니라 <b>남은 초로</b> 받는 것은 서버가 자기 시계로 마감 시각을 계산하게 해서
 * 브라우저와의 시차를 끌어오지 않으려는 것이다(#182, #183).
 *
 * <p>여기서 막는 것은 형식뿐이다. 실제 범위(트리거 초 이상, 지금 마감보다 앞)는 <b>경매방 설정과
 * 물품의 현재 마감 시각에 달려 있어</b> 요청만 보고는 알 수 없으므로 Service 가 검증한다.
 */
@Builder
public record AuctionItemCloseEarlyRequestDto(

        @Positive(message = "마감까지 남길 시간은 0보다 커야 합니다.")
        Integer remainingSeconds
) {
}
