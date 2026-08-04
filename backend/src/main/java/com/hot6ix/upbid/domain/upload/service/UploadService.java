package com.hot6ix.upbid.domain.upload.service;

import com.hot6ix.upbid.domain.upload.dto.request.PresignedUrlRequestDto;
import com.hot6ix.upbid.domain.upload.dto.response.PresignedUrlResponseDto;
import com.hot6ix.upbid.domain.upload.exception.UploadErrorType;
import com.hot6ix.upbid.global.config.AwsProperties;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class UploadService {

    private static final Duration EXPIRES_IN = Duration.ofMinutes(5);

    /**
     * 허용하는 content-type과 그때 붙일 확장자.
     *
     * <p>확장자를 클라이언트가 보낸 파일명에서 떼지 않고 여기서 역산한다. 파일명은 클라이언트가
     * 지어낸 값이라 {@code .jpg}로 끝나면서 내용이 다른 것을 올릴 수 있다.
     */
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final S3Presigner s3Presigner;
    private final AwsProperties awsProperties;

    /**
     * S3에 직접 올릴 수 있는 주소를 발급한다.
     *
     * <p>키 경로는 {@code {prefix}/{userId}/{uuid}.{ext}}이고 서버가 만든다. userId는 세션에서
     * 오므로 클라이언트가 다른 사람 폴더를 고를 수 없다.
     */
    public PresignedUrlResponseDto createPresignedUrl(Long userId, PresignedUrlRequestDto request) {

        // 받은 문자열을 소문자로 바꾸거나 공백을 떼지 않고 그대로 찾는다. 여기서 값을 손보면
        // 서명에 묶이는 content-type과 클라이언트가 PUT에 실어 보내는 값이 어긋나서,
        // 400 대신 S3의 SignatureDoesNotMatch 403으로 뒤늦게 실패한다.
        String contentType = request.contentType();
        String extension = EXTENSION_BY_CONTENT_TYPE.get(contentType);

        if (extension == null) {
            throw new ApplicationException(UploadErrorType.UNSUPPORTED_IMAGE_TYPE);
        }

        String key = "%s/%d/%s.%s".formatted(
                request.domain().getPrefix(), userId, UUID.randomUUID(), extension);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(awsProperties.s3().bucket())
                .key(key)
                .contentType(contentType)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(EXPIRES_IN)
                        .putObjectRequest(putObjectRequest)
                        .build());

        return PresignedUrlResponseDto.builder()
                .uploadUrl(presigned.url().toString())
                .fileUrl(awsProperties.s3().publicBaseUrl() + "/" + key)
                .expiresInSeconds(EXPIRES_IN.toSeconds())
                .build();
    }
}
