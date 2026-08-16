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
    AUCTION_ROOM_ALREADY_STARTED(HttpStatus.CONFLICT, 4003, "경매가 시작된 경매방은 수정할 수 없습니다."),
    AUCTION_ROOM_CLOSED(HttpStatus.CONFLICT, 4004, "종료된 경매방입니다."),
    PRODUCT_ALREADY_IN_AUCTION(HttpStatus.CONFLICT, 4005, "아직 경매·거래가 진행 중인 상품입니다."),
    AUCTION_ITEM_ALREADY_STARTED(HttpStatus.CONFLICT, 4006, "경매가 시작된 물품은 제외할 수 없습니다."),
    AUCTION_ITEM_NOT_READY(HttpStatus.CONFLICT, 4007, "대기 중인 물품만 시작할 수 있습니다."),
    AUCTION_ITEM_START_LIMIT_EXCEEDED(HttpStatus.CONFLICT, 4008,
            "한 경매방에서는 최대 3개 물품만 동시에 진행할 수 있습니다."),
    AUCTION_ITEM_LIMIT_EXCEEDED(HttpStatus.CONFLICT, 4009,
            "한 경매방에는 최대 100개 물품만 등록할 수 있습니다."),
    AUCTION_ITEM_NOT_IN_PROGRESS(HttpStatus.CONFLICT, 4010, "진행 중인 물품이 아닙니다."),
    AUCTION_ITEM_ALREADY_CLOSING_SOON(HttpStatus.CONFLICT, 4011,
            "이미 마감이 임박한 물품입니다."),
    AUCTION_ROOM_HAS_IN_PROGRESS_ITEM(HttpStatus.CONFLICT, 4012,
            "진행 중인 물품이 있어 경매방을 종료할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;
}
