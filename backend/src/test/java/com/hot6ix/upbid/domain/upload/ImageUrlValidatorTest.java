package com.hot6ix.upbid.domain.upload;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.upload.exception.UploadErrorType;
import com.hot6ix.upbid.global.config.AwsProperties;
import com.hot6ix.upbid.global.exception.ApplicationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageUrlValidatorTest {

    private static final String PUBLIC_BASE_URL = "https://upbid-bucket.s3.ap-northeast-2.amazonaws.com";

    private final ImageUrlValidator imageUrlValidator = new ImageUrlValidator(
            new AwsProperties("ap-northeast-2", new AwsProperties.S3("upbid-bucket", PUBLIC_BASE_URL)));

    @Test
    @DisplayName("우리 버킷에서 발급한 주소는 통과한다")
    void allowsUrlFromOurBucket() {

        assertThatCode(() -> imageUrlValidator.validate(
                PUBLIC_BASE_URL + "/products/1/6b1a0f2e-0000-0000-0000-000000000000.jpg"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미지를 올리지 않은 경우가 정상이라 null은 통과한다")
    void allowsNull() {

        assertThatCode(() -> imageUrlValidator.validate(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 도메인 주소는 막는다")
    void rejectsExternalUrl() {

        assertThatThrownBy(() -> imageUrlValidator.validate("https://cdn.example.com/product.png"))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(UploadErrorType.INVALID_IMAGE_URL);
    }

    @Test
    @DisplayName("우리 주소로 시작하기만 하는 다른 도메인은 막는다")
    void rejectsUrlThatOnlyStartsWithOurHost() {

        // 앞부분만 비교하면 통과해 버리는 주소다. 뒤에 "/"까지 붙여서 비교해야 막힌다.
        assertThatThrownBy(() -> imageUrlValidator.validate(
                PUBLIC_BASE_URL + ".example.com/products/1/x.jpg"))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(UploadErrorType.INVALID_IMAGE_URL);
    }

    @Test
    @DisplayName("public-base-url이 /로 끝나도 같은 주소를 통과시킨다")
    void allowsSameUrlWhenBaseUrlHasTrailingSlash() {

        ImageUrlValidator validator = new ImageUrlValidator(
                new AwsProperties("ap-northeast-2",
                        new AwsProperties.S3("upbid-bucket", PUBLIC_BASE_URL + "/")));

        assertThatCode(() -> validator.validate(PUBLIC_BASE_URL + "/products/1/x.jpg"))
                .doesNotThrowAnyException();
    }
}
