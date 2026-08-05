package com.hot6ix.upbid.domain.sse.dto;

/** 물품이 추가됐으니 목록을 다시 읽으라는 신호. 물품 자체는 담지 않는다({@code ItemAdded} 참고). */
public record ItemAddedDto(int addedCount) {
}
