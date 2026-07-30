package com.hot6ix.upbid.domain.auth.oauth.kakao.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KakaoProperties.class)
public class KakaoRestClientConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Bean
    public RestClient kakaoRestClient() {
        return RestClient.builder()
                .requestFactory(kakaoClientHttpRequestFactory())
                .build();
    }

    private ClientHttpRequestFactory kakaoClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }
}
