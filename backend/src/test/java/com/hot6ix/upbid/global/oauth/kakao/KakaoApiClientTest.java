package com.hot6ix.upbid.global.oauth.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hot6ix.upbid.domain.auth.oauth.kakao.KakaoApiClient;
import com.hot6ix.upbid.domain.auth.oauth.kakao.config.KakaoProperties;
import com.hot6ix.upbid.domain.auth.oauth.kakao.dto.KakaoTokenResponse;
import com.hot6ix.upbid.domain.auth.oauth.kakao.dto.KakaoUserInfoResponse;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class KakaoApiClientTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final KakaoProperties kakaoProperties = new KakaoProperties(
            "client-id", null, "http://localhost/callback", TOKEN_URI, USER_INFO_URI);

    private RestClient.Builder restClientBuilder = RestClient.builder();
    private MockRestServiceServer server;
    private KakaoApiClient kakaoApiClient;

    private void setUp() {
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        kakaoApiClient = new KakaoApiClient(restClientBuilder.build(), kakaoProperties);
    }

    @Test
    @DisplayName("정상 응답을 KakaoTokenResponse로 변환한다")
    void issueTokenSuccess() {
        setUp();
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"access_token": "access-token"}
                        """, MediaType.APPLICATION_JSON));

        KakaoTokenResponse response = kakaoApiClient.issueToken("code");

        assertThat(response.accessToken()).isEqualTo("access-token");
        server.verify();
    }

    @Test
    @DisplayName("정상 응답을 KakaoUserInfoResponse로 변환한다")
    void getUserInfoSuccess() {
        setUp();
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id": 1, "kakao_account": {"phone_number": "010-1234-5678", "email": "a@b.com"}}
                        """, MediaType.APPLICATION_JSON));

        KakaoUserInfoResponse response = kakaoApiClient.getUserInfo("access-token");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.kakaoAccount().phoneNumber()).isEqualTo("010-1234-5678");
        server.verify();
    }

    @Test
    @DisplayName("타임아웃이 발생하면 1회 재시도 후 성공한다")
    void retriesOnceOnTimeout() {
        setUp();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(request -> {
                    throw new IOException("timeout");
                });
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("""
                        {"access_token": "access-token"}
                        """, MediaType.APPLICATION_JSON));

        KakaoTokenResponse response = kakaoApiClient.issueToken("code");

        assertThat(response.accessToken()).isEqualTo("access-token");
        server.verify();
    }

    @Test
    @DisplayName("재시도도 실패하면 예외가 그대로 전파된다")
    void propagatesExceptionWhenRetryAlsoFails() {
        setUp();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(request -> {
                    throw new IOException("timeout");
                });
        server.expect(requestTo(TOKEN_URI))
                .andRespond(request -> {
                    throw new IOException("timeout");
                });

        assertThatThrownBy(() -> kakaoApiClient.issueToken("code"))
                .isInstanceOf(ResourceAccessException.class);
        server.verify();
    }

    @Test
    @DisplayName("카카오가 4xx 응답을 주면 재시도 없이 즉시 예외가 발생한다")
    void doesNotRetryOnClientError() {
        setUp();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("""
                                {"error": "invalid_grant"}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoApiClient.issueToken("code"))
                .isInstanceOf(HttpClientErrorException.class);
        server.verify();
    }
}
