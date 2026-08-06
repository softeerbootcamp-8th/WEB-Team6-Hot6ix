package com.hot6ix.upbid.domain.sse.dto;

/**
 * 경매방 설정이 바뀌었으니 방 정보를 다시 읽으라는 신호.
 *
 * <p>담을 값이 없어 비어 있다({@code {}}로 나간다). 바뀐 필드를 실으면 payload가 수정 가능한
 * 필드 목록을 따라다녀야 하는데, 화면은 어차피 방 정보를 통째로 다시 그린다.
 */
public record RoomUpdatedDto() {
}
