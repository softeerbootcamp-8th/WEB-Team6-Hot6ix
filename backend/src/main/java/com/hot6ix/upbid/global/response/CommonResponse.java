package com.hot6ix.upbid.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hot6ix.upbid.global.exception.ErrorType;
import com.hot6ix.upbid.global.exception.ValidationError;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommonResponse<T>(
        boolean success,
        T data,
        Integer code,
        String message,
        List<ValidationError> errors
) {
    public static <T> CommonResponse<T> ok(T data, String message) {
        return new CommonResponse<>(true, data, null, message, null);
    }

    public static CommonResponse<Void> ok(String message) {
        return new CommonResponse<>(true, null, null, message, null);
    }

    public static CommonResponse<Void> error(ErrorType errorType) {
        return new CommonResponse<>(false, null, errorType.getErrorCode(), errorType.getMessage(), null);
    }

    public static CommonResponse<Void> error(ErrorType errorType, List<ValidationError> errors) {
        return new CommonResponse<>(false, null, errorType.getErrorCode(), errorType.getMessage(), errors);
    }
}
