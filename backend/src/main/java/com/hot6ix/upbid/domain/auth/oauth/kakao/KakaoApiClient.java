package com.hot6ix.upbid.domain.auth.oauth.kakao;

import com.hot6ix.upbid.domain.auth.oauth.kakao.config.KakaoProperties;
import com.hot6ix.upbid.domain.auth.oauth.kakao.dto.KakaoTokenResponse;
import com.hot6ix.upbid.domain.auth.oauth.kakao.dto.KakaoUserInfoResponse;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class KakaoApiClient {

    private final RestClient kakaoRestClient;
    private final KakaoProperties kakaoProperties;

    public KakaoTokenResponse issueToken(String authorizationCode) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", kakaoProperties.clientId());
        body.add("redirect_uri", kakaoProperties.redirectUri());
        body.add("code", authorizationCode);
        if (StringUtils.hasText(kakaoProperties.clientSecret())) {
            body.add("client_secret", kakaoProperties.clientSecret());
        }

        return kakaoRestClient.post()
                .uri(kakaoProperties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(KakaoTokenResponse.class);
    }

    public KakaoUserInfoResponse getUserInfo(String accessToken) {
        return executeWithRetry(() -> kakaoRestClient.get()
                .uri(kakaoProperties.userInfoUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(KakaoUserInfoResponse.class));
    }

    private <T> T executeWithRetry(Supplier<T> request) {
        try {
            return request.get();
        } catch (ResourceAccessException e) {
            return request.get();
        }
    }
}
