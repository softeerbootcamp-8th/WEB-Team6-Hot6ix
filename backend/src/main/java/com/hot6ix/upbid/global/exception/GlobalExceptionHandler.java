package com.hot6ix.upbid.global.exception;

import com.hot6ix.upbid.global.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<CommonResponse<Void>> handleApplicationException(
            ApplicationException e, HttpServletRequest request) {

        ErrorType errorType = e.getErrorType();

        log.warn("애플리케이션 예외 발생 - [{}] {} {} ({})",
                request.getMethod(), request.getRequestURI(),
                errorType.getErrorCode(), errorType.getMessage());

        return ResponseEntity.status(errorType.getHttpStatus())
                .body(CommonResponse.error(errorType));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {

        List<ValidationError> errors = e.getBindingResult().getAllErrors()
                .stream()
                .map(GlobalExceptionHandler::toValidationError)
                .toList();

        log.warn("유효성 검증 실패 - [{}] {} errors={}",
                request.getMethod(), request.getRequestURI(), errors);

        return ResponseEntity.status(CommonErrorType.INVALID_REQUEST.getHttpStatus())
                .body(CommonResponse.error(CommonErrorType.INVALID_REQUEST, errors));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CommonResponse<Void>> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpServletRequest request) {

        List<ValidationError> errors = e.getParameterValidationResults()
                .stream()
                .flatMap(GlobalExceptionHandler::toValidationErrors)
                .toList();

        log.warn("유효성 검증 실패 - [{}] {} errors={}",
                request.getMethod(), request.getRequestURI(), errors);

        return ResponseEntity.status(CommonErrorType.INVALID_REQUEST.getHttpStatus())
                .body(CommonResponse.error(CommonErrorType.INVALID_REQUEST, errors));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {

        log.warn("허용되지 않은 HTTP 메서드 - [{}] {}", request.getMethod(), request.getRequestURI());

        return ResponseEntity.status(CommonErrorType.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(CommonResponse.error(CommonErrorType.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        log.warn("경로 변수 타입 불일치 - [{}] {} name={} value={}",
                request.getMethod(), request.getRequestURI(), e.getName(), e.getValue());

        return ResponseEntity.status(CommonErrorType.INVALID_REQUEST.getHttpStatus())
                .body(CommonResponse.error(CommonErrorType.INVALID_REQUEST));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponse<Void>> handleNoResourceFoundException(
            NoResourceFoundException e, HttpServletRequest request) {

        log.warn("리소스를 찾을 수 없음 - [{}] {}", request.getMethod(), request.getRequestURI());

        return ResponseEntity.status(CommonErrorType.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(CommonResponse.error(CommonErrorType.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleException(
            Exception e, HttpServletRequest request) {

        log.error("예상치 못한 예외 발생 - [{}] {} ({})", request.getMethod(), request.getRequestURI(), e.getMessage(), e);

        return ResponseEntity.status(CommonErrorType.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(CommonResponse.error(CommonErrorType.INTERNAL_SERVER_ERROR));
    }

    private static Stream<ValidationError> toValidationErrors(ParameterValidationResult result) {

        if (result instanceof ParameterErrors parameterErrors) {
            return parameterErrors.getAllErrors().stream()
                    .map(GlobalExceptionHandler::toValidationError);
        }

        String field = Objects.requireNonNullElse(
                result.getMethodParameter().getParameterName(), "unknown");

        return result.getResolvableErrors().stream()
                .map(error -> new ValidationError(field, error.getDefaultMessage()));
    }

    private static ValidationError toValidationError(ObjectError error) {

        String field = error instanceof FieldError fieldError
                ? fieldError.getField()
                : error.getObjectName();

        return new ValidationError(field, error.getDefaultMessage());
    }
}
