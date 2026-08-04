package com.hot6ix.upbid.domain.auth.exception;

import com.hot6ix.upbid.global.exception.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorType implements ErrorType {

    KAKAO_TOKEN_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, 1001, "카카오 인증 서버와 통신에 실패했습니다."),
    KAKAO_USER_INFO_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, 1002, "카카오 사용자 정보 조회에 실패했습니다."),
    KAKAO_PHONE_NUMBER_REQUIRED(HttpStatus.BAD_REQUEST, 1003, "카카오 계정에 휴대폰 번호가 등록되어 있지 않습니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, 1004, "지원하지 않는 로그인 방식입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 1005, "로그인이 필요합니다."),
    OAUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, 1006, "소셜 로그인에 실패했습니다."),
    WITHDRAWN_USER(HttpStatus.FORBIDDEN, 1007, "탈퇴한 회원입니다."),
    USER_INFO_INVALID(HttpStatus.BAD_GATEWAY, 1008, "사용자 정보가 정책에 위배되어 처리할 수 없습니다."),
    PENDING_SIGNUP_NOT_FOUND(HttpStatus.UNAUTHORIZED, 1009, "가입 진행 정보가 없거나 만료되었습니다. 다시 로그인해 주세요."),
    PHONE_NOT_VERIFIED(HttpStatus.BAD_REQUEST, 1010, "전화번호 인증이 완료되지 않았습니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;
}
