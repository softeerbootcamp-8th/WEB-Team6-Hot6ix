package com.hot6ix.upbid.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        String region,
        S3 s3
) {
    // publicBaseUrl은 업로드한 이미지를 읽는 주소의 앞부분이다. bucket·region에서 조립하지 않고
    // 따로 받는다. CloudFront를 앞에 두면 이 값만 그 도메인으로 바뀌고 bucket은 그대로이기 때문이다.
    public record S3(
            String bucket,
            String publicBaseUrl
    ) {
    }
}
