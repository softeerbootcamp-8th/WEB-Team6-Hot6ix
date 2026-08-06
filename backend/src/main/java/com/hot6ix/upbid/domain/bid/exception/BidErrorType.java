package com.hot6ix.upbid.domain.bid.exception;

import com.hot6ix.upbid.global.exception.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 입찰 도메인 에러. 코드 블록은 7xxx다.
 *
 * <p>물품이 없을 때는 여기에 정의하지 않고
 * {@code AuctionItemErrorType.AUCTION_ITEM_NOT_FOUND}(4001)를, 입찰자가 없을 때는
 * {@code CommonErrorType.RESOURCE_NOT_FOUND}(2003)를 재사용한다.
 */
@Getter
@RequiredArgsConstructor
public enum BidErrorType implements ErrorType {

    ITEM_NOT_IN_PROGRESS(HttpStatus.CONFLICT, 7001, "진행중인 물품이 아닙니다."),
    ITEM_CLOSED(HttpStatus.CONFLICT, 7002, "마감된 물품입니다."),
    ALREADY_TOP_BIDDER(HttpStatus.CONFLICT, 7003, "이미 최고 입찰자입니다."),
    BID_AMOUNT_TOO_LOW(HttpStatus.CONFLICT, 7004, "최소 입찰 금액보다 낮습니다."),
    INVALID_BID_UNIT(HttpStatus.CONFLICT, 7005, "입찰 단위에 맞지 않는 금액입니다."),
    CONCURRENT_BID_CONFLICT(HttpStatus.CONFLICT, 7006, "다른 분이 먼저 입찰했습니다. 현재가를 확인해 주세요."),
    SELLER_CANNOT_BID(HttpStatus.FORBIDDEN, 7007, "판매자는 자신의 물품에 입찰할 수 없습니다."),
    TERMS_NOT_AGREED(HttpStatus.FORBIDDEN, 7008, "경매방 입장 약관에 동의한 회원만 입찰할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;
}
