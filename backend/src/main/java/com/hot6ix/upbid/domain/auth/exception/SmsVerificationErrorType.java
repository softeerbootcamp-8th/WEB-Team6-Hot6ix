package com.hot6ix.upbid.domain.auth.exception;

import com.hot6ix.upbid.global.exception.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 8xxx — SMS 인증
 */
@Getter
@RequiredArgsConstructor
public enum SmsVerificationErrorType implements ErrorType {

    SMS_SEND_FAILED(HttpStatus.BAD_GATEWAY, 8001, "SMS 발송에 실패했습니다."),
    CODE_NOT_FOUND(HttpStatus.BAD_REQUEST, 8002, "인증번호를 먼저 요청해주세요."),
    CODE_EXPIRED(HttpStatus.BAD_REQUEST, 8003, "인증번호가 만료되었습니다. 다시 발급해주세요."),
    CODE_MISMATCH(HttpStatus.BAD_REQUEST, 8004, "인증번호가 일치하지 않습니다."),
    CODE_MAX_FAIL_EXCEEDED(HttpStatus.BAD_REQUEST, 8005, "인증번호 입력 횟수를 초과했습니다. 다시 발급해주세요."),
    SEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, 8006, "인증번호 재발송은 1분 후에 가능합니다."),
    SEND_HOURLY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, 8007, "1시간 내 인증번호 발송 횟수를 초과했습니다."),
    SEND_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, 8008, "오늘 인증번호 발송 횟수를 초과했습니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;
}
