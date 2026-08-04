package com.hot6ix.upbid.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(AwsProperties.class)
@RequiredArgsConstructor
public class S3Config {

    private final AwsProperties awsProperties;

    /**
     * credentialsProvider를 지정하지 않는다. 기본값인 {@code DefaultCredentialsProvider}가
     * 환경마다 알아서 찾는다 — 배포된 app EC2에서는 붙여 둔 인스턴스 역할 upbid-ec2-role을,
     * 로컬에서는 환경변수나 {@code ~/.aws/credentials}를 쓴다. 그래서 액세스 키를 코드에도
     * 설정에도 두지 않는다.
     *
     * <p>빈을 만드는 시점에는 자격증명을 찾지 않는다. 서명할 때 찾는다. 그래서 자격증명이
     * 없는 환경에서도 앱은 뜨고, {@code /api/v1/uploads/presigned-url}을 부를 때만 실패한다.
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(awsProperties.region()))
                .build();
    }
}
