package com.hot6ix.upbid.domain.upload;

import com.hot6ix.upbid.domain.upload.exception.UploadErrorType;
import com.hot6ix.upbid.global.config.AwsProperties;
import com.hot6ix.upbid.global.exception.ApplicationException;
import org.springframework.stereotype.Component;

/**
 * 저장하려는 이미지 주소가 우리 버킷에서 온 것인지 본다.
 *
 * <p>{@code storeImageUrl}·{@code imageUrl}은 클라이언트가 문자열로 보낸다. 확인하지 않으면
 * 남의 서버 주소가 상품 사진으로 저장되고, 그 서버가 무엇을 보여줄지는 우리가 못 정한다.
 *
 * <p>{@link UploadService}가 발급한 {@code fileUrl}만 통과한다.
 */
@Component
public class ImageUrlValidator {

    private final String requiredPrefix;

    public ImageUrlValidator(AwsProperties awsProperties) {
        String publicBaseUrl = awsProperties.s3().publicBaseUrl();

        // 뒤에 "/"를 붙여서 비교한다. 붙이지 않으면 publicBaseUrl로 시작하기만 하면 통과해서
        // https://upbid-bucket.s3.ap-northeast-2.amazonaws.com.example.com/x.jpg 같은
        // 남의 도메인이 들어온다.
        this.requiredPrefix = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
    }

    /**
     * @param imageUrl 저장하려는 이미지 주소. 이미지를 올리지 않은 경우가 정상이라 null은 통과시킨다.
     * @throws ApplicationException 우리 버킷 주소가 아닐 때(INVALID_IMAGE_URL)
     */
    public void validate(String imageUrl) {

        if (imageUrl == null) {
            return;
        }

        if (!imageUrl.startsWith(requiredPrefix)) {
            throw new ApplicationException(UploadErrorType.INVALID_IMAGE_URL);
        }
    }
}
