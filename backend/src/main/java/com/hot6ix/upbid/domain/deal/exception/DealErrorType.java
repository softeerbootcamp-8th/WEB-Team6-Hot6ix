package com.hot6ix.upbid.domain.deal.exception;

import com.hot6ix.upbid.global.exception.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 거래·낙찰 도메인 에러. 코드 블록은 6xxx다.
 *
 * <p>물품이 없을 때는 여기에 정의하지 않고
 * {@code AuctionItemErrorType.AUCTION_ITEM_NOT_FOUND}(4001)를 재사용한다.
 */
@Getter
@RequiredArgsConstructor
public enum DealErrorType implements ErrorType {

    NOT_DEAL_OWNER(HttpStatus.FORBIDDEN, 6001, "해당 물품의 판매자만 거래 상태를 변경할 수 있습니다."),
    ITEM_NOT_SOLD(HttpStatus.CONFLICT, 6002, "낙찰이 확정되지 않은 물품입니다."),
    DEAL_CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, 6003, "낙찰 후보를 찾을 수 없습니다."),
    DEAL_CANDIDATE_ALREADY_RESOLVED(HttpStatus.CONFLICT, 6004, "이미 처리된 낙찰 후보입니다."),
    DEAL_CANDIDATE_NOT_ACTIVE(HttpStatus.CONFLICT, 6005, "현재 낙찰 권한을 가진 후보가 아닙니다."),
    DEAL_ALREADY_COMPLETED(HttpStatus.CONFLICT, 6006, "이미 완료된 거래입니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;
}
