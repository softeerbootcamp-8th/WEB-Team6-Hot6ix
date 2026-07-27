package com.hot6ix.upbid.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorType {

    HttpStatus getHttpStatus();
    Integer getErrorCode();
    String getMessage();
}
