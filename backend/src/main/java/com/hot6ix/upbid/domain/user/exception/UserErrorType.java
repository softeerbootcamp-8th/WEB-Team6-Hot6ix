package com.hot6ix.upbid.domain.user.exception;

import com.hot6ix.upbid.global.exception.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorType implements ErrorType {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, 2001, "존재하지 않는 회원입니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;
}
