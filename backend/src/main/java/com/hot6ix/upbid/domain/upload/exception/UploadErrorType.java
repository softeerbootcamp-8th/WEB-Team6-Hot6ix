package com.hot6ix.upbid.domain.upload.exception;

import com.hot6ix.upbid.global.exception.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UploadErrorType implements ErrorType {

    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, 10001,
            "지원하지 않는 이미지 형식입니다. image/jpeg, image/png, image/webp만 올릴 수 있습니다."),
    INVALID_IMAGE_URL(HttpStatus.BAD_REQUEST, 10002,
            "이미지 주소가 올바르지 않습니다. 업로드 URL 발급 API로 받은 주소만 저장할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;
}
