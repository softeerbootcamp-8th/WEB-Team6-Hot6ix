package com.hot6ix.upbid.domain.user.exception;

import com.hot6ix.upbid.global.exception.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SellerProfileErrorType implements ErrorType {

    DUPLICATE_SELLER_PROFILE(HttpStatus.CONFLICT, 3001, "이미 등록된 판매자 프로필이 있습니다."),
    SELLER_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, 3002, "판매자 프로필을 찾을 수 없습니다."),
    SELLER_PROFILE_IN_USE(HttpStatus.CONFLICT, 3003, "진행 중인 경매방이 있어 삭제할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;
}
