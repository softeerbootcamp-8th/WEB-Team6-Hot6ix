package com.hot6ix.upbid.domain.auction.exception;

import com.hot6ix.upbid.global.exception.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuctionErrorType implements ErrorType {

    AUCTION_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, 4001, "존재하지 않는 경매 물품입니다."),
    AUCTION_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, 4002, "존재하지 않는 경매방입니다."),
    AUCTION_ROOM_ALREADY_STARTED(HttpStatus.CONFLICT, 4003, "경매가 시작된 경매방은 수정할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;
}
