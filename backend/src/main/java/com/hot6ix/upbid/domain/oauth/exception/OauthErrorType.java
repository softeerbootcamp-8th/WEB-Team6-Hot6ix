package com.hot6ix.upbid.domain.oauth.exception;

import com.hot6ix.upbid.global.exception.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OauthErrorType implements ErrorType {

    KAKAO_TOKEN_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, 4001, "카카오 인증 서버와 통신에 실패했습니다."),
    KAKAO_USER_INFO_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, 4002, "카카오 사용자 정보 조회에 실패했습니다."),
    KAKAO_PHONE_NUMBER_REQUIRED(HttpStatus.BAD_REQUEST, 4003, "카카오 계정에 휴대폰 번호가 등록되어 있지 않습니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, 4004, "지원하지 않는 로그인 방식입니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;
}
