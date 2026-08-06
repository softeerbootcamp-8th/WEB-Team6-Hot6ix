package com.hot6ix.upbid.domain.sse.dto;

/** 물품이 빠졌으니 목록을 다시 읽으라는 신호. {@code itemId}는 어느 물품인지 알려줄 뿐이다. */
public record ItemRemovedDto(Long itemId) {
}
