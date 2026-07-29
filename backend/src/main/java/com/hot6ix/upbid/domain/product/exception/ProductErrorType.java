package com.hot6ix.upbid.domain.product.exception;

import com.hot6ix.upbid.global.exception.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorType implements ErrorType {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, 5001, "상품을 찾을 수 없습니다."),
    PRODUCT_AUCTION_ALREADY_STARTED(HttpStatus.CONFLICT, 5002, "경매방이 시작된 상품은 수정·삭제할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;
}
