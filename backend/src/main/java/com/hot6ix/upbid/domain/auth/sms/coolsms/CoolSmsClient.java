package com.hot6ix.upbid.domain.auth.sms.coolsms;

import com.hot6ix.upbid.domain.auth.exception.SmsVerificationErrorType;
import com.hot6ix.upbid.domain.auth.sms.coolsms.config.CoolSmsProperties;
import com.hot6ix.upbid.global.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.stereotype.Component;

/**
 * CoolSMS SDK를 래핑한 SMS 발송 클라이언트.
 *
 * <p>{@link DefaultMessageService}는 {@link NurigoApp}을 통해 초기화하며,
 * API 키·시크릿·발신번호는 {@link CoolSmsProperties}에서 주입받는다.
 *
 * <p>발송 실패 시 {@link SmsVerificationErrorType#SMS_SEND_FAILED}로 변환해 던진다.
 * SDK 내부 예외를 그대로 전파하지 않아 상위 레이어가 외부 SDK에 직접 의존하지 않도록 한다.
 */
@Slf4j
@Component
public class CoolSmsClient {

    private static final String COOLSMS_API_URL = "https://api.coolsms.co.kr";

    private final DefaultMessageService messageService;
    private final CoolSmsProperties properties;

    /**
     * CoolSMS SDK를 초기화한다.
     * {@link NurigoApp#initialize}는 API 키·시크릿으로 인증된 {@link DefaultMessageService}를 반환한다.
     */
    public CoolSmsClient(CoolSmsProperties properties) {
        this.properties = properties;
        this.messageService = NurigoApp.INSTANCE.initialize(
                properties.apiKey(), properties.apiSecret(), COOLSMS_API_URL
        );
    }

    /**
     * 인증번호를 SMS로 발송한다.
     *
     * @param to   수신 전화번호 (하이픈 없이, 예: 01012345678)
     * @param code 발송할 6자리 인증번호
     * @throws ApplicationException 발송 실패 시 {@link SmsVerificationErrorType#SMS_SEND_FAILED}
     */
    public void sendVerificationCode(String to, String code) {
        Message message = new Message();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setText("[Upbid] 인증번호: " + code + "\n인증번호를 입력해주세요.");

        try {
            SingleMessageSentResponse response = messageService.sendOne(
                    new SingleMessageSendingRequest(message)
            );
            log.info("SMS 발송 완료 - to={}, statusCode={}", to, response.getStatusCode());
        } catch (Exception e) {
            log.error("SMS 발송 실패 - to={}", to, e);
            throw new ApplicationException(SmsVerificationErrorType.SMS_SEND_FAILED);
        }
    }
}
