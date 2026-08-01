package com.hot6ix.upbid.domain.auth.sms.naversens;

import com.hot6ix.upbid.domain.auth.exception.SmsVerificationErrorType;
import com.hot6ix.upbid.domain.auth.sms.naversens.config.NaverSensProperties;
import com.hot6ix.upbid.global.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 네이버 클라우드 플랫폼 SENS REST API를 호출하는 SMS 발송 클라이언트.
 *
 * <p>SENS는 REST API로 직접 호출한다.
 * 인증은 요청마다 HMAC-SHA256 서명을 생성해 헤더에 담는 방식이다.
 *
 * <p>발송 실패 시 {@link SmsVerificationErrorType#SMS_SEND_FAILED}로 변환해 던진다.
 * HTTP 오류나 서명 오류 등 외부 의존성 예외를 그대로 전파하지 않아
 * 상위 레이어가 SENS API에 직접 의존하지 않도록 한다.
 */
@Slf4j
@Component
public class NaverSmsClient {

    private static final String BASE_URL = "https://sens.apigw.ntruss.com";

    private final NaverSensProperties properties;
    private final RestClient restClient;

    public NaverSmsClient(NaverSensProperties properties) {
        this.properties = properties;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .build();
    }

    /**
     * 인증번호를 SMS로 발송한다.
     *
     * @param to   수신 전화번호 (하이픈 없이, 예: 01012345678)
     * @param code 발송할 6자리 인증번호
     * @throws ApplicationException 발송 실패 시 {@link SmsVerificationErrorType#SMS_SEND_FAILED}
     */
    public void sendVerificationCode(String to, String code) {
        String url = "/sms/v2/services/" + properties.serviceId() + "/messages";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = buildSignature(url, timestamp);

        Map<String, Object> body = Map.of(
                "type", "SMS",
                "contentType", "COMM",
                "countryCode", "82",
                "from", properties.sendFrom(),
                "content", "[Upbid] 인증번호: " + code + "\n인증번호를 입력해주세요.",
                "messages", List.of(Map.of("to", to))
        );

        try {
            restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("x-ncp-apigw-timestamp", timestamp)
                    .header("x-ncp-iam-access-key", properties.accessKey())
                    .header("x-ncp-apigw-signature-v2", signature)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("SMS 발송 완료 - to={}", to);
        } catch (HttpClientErrorException e) {
            // 4xx — 잘못된 요청이나 인증 실패 (서명 오류, 잘못된 serviceId 등)
            log.warn("SENS API 클라이언트 오류 - to={}, status={}, body={}", to, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApplicationException(SmsVerificationErrorType.SMS_SEND_FAILED);
        } catch (HttpServerErrorException e) {
            // 5xx — SENS 서버 측 문제
            log.error("SENS API 서버 오류 - to={}, status={}", to, e.getStatusCode());
            throw new ApplicationException(SmsVerificationErrorType.SMS_SEND_FAILED);
        } catch (ResourceAccessException e) {
            // 타임아웃, 네트워크 단절 등 연결 수준의 문제
            log.warn("SENS API 연결 오류 - to={}, message={}", to, e.getMessage());
            throw new ApplicationException(SmsVerificationErrorType.SMS_SEND_FAILED);
        }
    }

    /**
     * SENS API 요청에 필요한 HMAC-SHA256 서명을 생성한다.
     *
     * <p>서명 대상 문자열: {@code "POST {url}\n{timestamp}\n{accessKey}"}
     * 이를 secretKey로 HmacSHA256 해시한 뒤 Base64로 인코딩한다.
     */
    private String buildSignature(String url, String timestamp) {
        String message = "POST " + url + "\n" + timestamp + "\n" + properties.accessKey();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    properties.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SENS 서명 생성 실패", e);
        }
    }
}
