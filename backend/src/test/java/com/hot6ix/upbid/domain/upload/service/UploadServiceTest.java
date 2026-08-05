package com.hot6ix.upbid.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.upload.UploadDomain;
import com.hot6ix.upbid.domain.upload.dto.request.PresignedUrlRequestDto;
import com.hot6ix.upbid.domain.upload.dto.response.PresignedUrlResponseDto;
import com.hot6ix.upbid.domain.upload.exception.UploadErrorType;
import com.hot6ix.upbid.global.config.AwsProperties;
import com.hot6ix.upbid.global.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class UploadServiceTest {

    private static final String BUCKET = "upbid-bucket";
    private static final String PUBLIC_BASE_URL = "https://upbid-bucket.s3.ap-northeast-2.amazonaws.com";
    private static final Long USER_ID = 42L;

    private UploadService uploadService;

    /**
     * presigner를 목으로 바꾸지 않고 실제 객체를 쓴다. 서명은 로컬 연산이라 AWS를 호출하지 않는다.
     *
     * <p>다만 자격증명은 서명할 때 찾는다. 기본값인 {@code DefaultCredentialsProvider}를 그대로
     * 두면 CI 러너에는 환경변수도 {@code ~/.aws/credentials}도 없고 EC2 메타데이터에도 닿지 않아
     * {@code SdkClientException}이 난다. 그래서 여기서는 더미 키를 직접 넣는다. 서명 값이 맞는지는
     * 어차피 이 테스트가 검증하지 않는다 — 확장자 역산과 키 조립만 본다.
     */
    @BeforeEach
    void setUp() {
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build();

        AwsProperties awsProperties = new AwsProperties(
                Region.AP_NORTHEAST_2.id(),
                new AwsProperties.S3(BUCKET, PUBLIC_BASE_URL));

        uploadService = new UploadService(presigner, awsProperties);
    }

    @Test
    @DisplayName("허용하지 않는 content-type이면 UNSUPPORTED_IMAGE_TYPE으로 막는다")
    void rejectsUnsupportedContentType() {

        PresignedUrlRequestDto request = PresignedUrlRequestDto.builder()
                .domain(UploadDomain.PRODUCT)
                .contentType("application/pdf")
                .build();

        assertThatThrownBy(() -> uploadService.createPresignedUrl(USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(UploadErrorType.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    @DisplayName("content-type의 대소문자가 다르면 받지 않는다")
    void rejectsContentTypeWithDifferentCase() {

        // 소문자로 고쳐서 받아 주면 서명에 묶인 값과 클라이언트가 PUT에 실을 값이 어긋나
        // 400 대신 S3의 403으로 뒤늦게 실패한다.
        PresignedUrlRequestDto request = PresignedUrlRequestDto.builder()
                .domain(UploadDomain.PRODUCT)
                .contentType("IMAGE/JPEG")
                .build();

        assertThatThrownBy(() -> uploadService.createPresignedUrl(USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(UploadErrorType.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    @DisplayName("키는 {prefix}/{userId}/{uuid}.{ext} 형식으로 서버가 만든다")
    void buildsKeyFromDomainAndUserId() {

        PresignedUrlResponseDto response = uploadService.createPresignedUrl(
                USER_ID,
                PresignedUrlRequestDto.builder()
                        .domain(UploadDomain.SELLER_PROFILE)
                        .contentType("image/jpeg")
                        .build());

        assertThat(response.fileUrl())
                .startsWith(PUBLIC_BASE_URL + "/seller-profiles/" + USER_ID + "/")
                .endsWith(".jpg");
    }

    @Test
    @DisplayName("경매방 커버는 auction-room-covers 아래로 간다")
    void buildsKeyForAuctionRoomCover() {

        PresignedUrlResponseDto response = uploadService.createPresignedUrl(
                USER_ID,
                PresignedUrlRequestDto.builder()
                        .domain(UploadDomain.AUCTION_ROOM_COVER)
                        .contentType("image/jpeg")
                        .build());

        assertThat(response.fileUrl())
                .startsWith(PUBLIC_BASE_URL + "/auction-room-covers/" + USER_ID + "/")
                .endsWith(".jpg");
    }

    @Test
    @DisplayName("content-type에 맞는 확장자를 붙인다")
    void derivesExtensionFromContentType() {

        PresignedUrlResponseDto png = uploadService.createPresignedUrl(
                USER_ID,
                PresignedUrlRequestDto.builder()
                        .domain(UploadDomain.PRODUCT)
                        .contentType("image/png")
                        .build());

        PresignedUrlResponseDto webp = uploadService.createPresignedUrl(
                USER_ID,
                PresignedUrlRequestDto.builder()
                        .domain(UploadDomain.PRODUCT)
                        .contentType("image/webp")
                        .build());

        assertThat(png.fileUrl()).endsWith(".png");
        assertThat(webp.fileUrl()).endsWith(".webp");
    }

    @Test
    @DisplayName("같은 요청을 두 번 해도 키가 겹치지 않는다")
    void generatesDifferentKeyForEachRequest() {

        PresignedUrlRequestDto request = PresignedUrlRequestDto.builder()
                .domain(UploadDomain.PRODUCT)
                .contentType("image/jpeg")
                .build();

        PresignedUrlResponseDto first = uploadService.createPresignedUrl(USER_ID, request);
        PresignedUrlResponseDto second = uploadService.createPresignedUrl(USER_ID, request);

        assertThat(first.fileUrl()).isNotEqualTo(second.fileUrl());
    }

    @Test
    @DisplayName("uploadUrl은 버킷 주소와 키를 담고 만료 시간은 5분이다")
    void signsUploadUrlForTheSameKey() {

        PresignedUrlResponseDto response = uploadService.createPresignedUrl(
                USER_ID,
                PresignedUrlRequestDto.builder()
                        .domain(UploadDomain.PRODUCT)
                        .contentType("image/jpeg")
                        .build());

        String key = response.fileUrl().substring(PUBLIC_BASE_URL.length() + 1);

        assertThat(response.uploadUrl()).contains(BUCKET).contains(key);
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
    }
}
