package com.hot6ix.upbid.domain.auth.exception;

import com.hot6ix.upbid.global.exception.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorType implements ErrorType {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 6001, "로그인이 필요합니다."),
    OAUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, 6002, "소셜 로그인에 실패했습니다."),
    WITHDRAWN_USER(HttpStatus.FORBIDDEN, 6003, "탈퇴한 회원입니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;
}
