package com.hot6ix.upbid.domain.auth.controller;

import com.hot6ix.upbid.domain.auth.dto.request.SmsSendRequestDto;
import com.hot6ix.upbid.domain.auth.dto.request.SmsVerifyRequestDto;
import com.hot6ix.upbid.domain.auth.exception.SmsVerificationErrorType;
import com.hot6ix.upbid.domain.auth.service.SmsVerificationService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SmsVerificationController.class)
@Import(GlobalExceptionHandler.class)
class SmsVerificationControllerTest extends AbstractControllerTest {

    private static final String SEND_URL = "/api/v1/auth/phone/send";
    private static final String VERIFY_URL = "/api/v1/auth/phone/verify";
    private static final String PHONE_NUMBER = "01012345678";
    private static final String VALID_CODE = "123456";

    @MockitoBean
    private SmsVerificationService smsVerificationService;

    // ==================== POST /send ====================

    @Test
    @DisplayName("올바른 전화번호로 발송 요청하면 200을 반환한다")
    void sendCode_성공() throws Exception {
        doNothing().when(smsVerificationService).sendCode(any());

        mockMvc.perform(post(SEND_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SmsSendRequestDto(PHONE_NUMBER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("인증번호가 발송되었습니다."));

        verify(smsVerificationService).sendCode(PHONE_NUMBER);
    }

    @Test
    @DisplayName("비로그인 상태로 발송 요청해도 200을 반환한다")
    void sendCode_비로그인_허용() throws Exception {
        비로그인_상태로_바꾼다();
        doNothing().when(smsVerificationService).sendCode(any());

        mockMvc.perform(post(SEND_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SmsSendRequestDto(PHONE_NUMBER))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("전화번호 형식이 잘못되면 400을 반환한다")
    void sendCode_잘못된_전화번호_형식() throws Exception {
        mockMvc.perform(post(SEND_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SmsSendRequestDto("010-1234-5678"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    @DisplayName("요청 바디가 없으면 400을 반환한다")
    void sendCode_바디_없음() throws Exception {
        mockMvc.perform(post(SEND_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    @DisplayName("쿨다운 중 재발송하면 429를 반환한다")
    void sendCode_쿨다운() throws Exception {
        doThrow(new ApplicationException(SmsVerificationErrorType.SEND_COOLDOWN))
                .when(smsVerificationService).sendCode(any());

        mockMvc.perform(post(SEND_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SmsSendRequestDto(PHONE_NUMBER))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(8006));
    }

    @Test
    @DisplayName("SMS 발송에 실패하면 502를 반환한다")
    void sendCode_SMS_발송_실패() throws Exception {
        doThrow(new ApplicationException(SmsVerificationErrorType.SMS_SEND_FAILED))
                .when(smsVerificationService).sendCode(any());

        mockMvc.perform(post(SEND_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SmsSendRequestDto(PHONE_NUMBER))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(8001));
    }

    // ==================== POST /verify ====================

    @Test
    @DisplayName("올바른 인증번호로 검증 요청하면 200을 반환한다")
    void verifyCode_성공() throws Exception {
        doNothing().when(smsVerificationService).verifyCode(any(), any());

        mockMvc.perform(post(VERIFY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SmsVerifyRequestDto(PHONE_NUMBER, VALID_CODE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("인증이 완료되었습니다."));

        verify(smsVerificationService).verifyCode(PHONE_NUMBER, VALID_CODE);
    }

    @Test
    @DisplayName("비로그인 상태로 검증 요청해도 200을 반환한다")
    void verifyCode_비로그인_허용() throws Exception {
        비로그인_상태로_바꾼다();
        doNothing().when(smsVerificationService).verifyCode(any(), any());

        mockMvc.perform(post(VERIFY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SmsVerifyRequestDto(PHONE_NUMBER, VALID_CODE))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증번호가 6자리 숫자가 아니면 400을 반환한다")
    void verifyCode_잘못된_인증번호_형식() throws Exception {
        mockMvc.perform(post(VERIFY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SmsVerifyRequestDto(PHONE_NUMBER, "12345"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    @DisplayName("인증번호가 만료됐으면 400을 반환한다")
    void verifyCode_인증번호_만료() throws Exception {
        doThrow(new ApplicationException(SmsVerificationErrorType.CODE_EXPIRED))
                .when(smsVerificationService).verifyCode(any(), any());

        mockMvc.perform(post(VERIFY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SmsVerifyRequestDto(PHONE_NUMBER, VALID_CODE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(8003));
    }

    @Test
    @DisplayName("인증번호가 틀리면 400을 반환한다")
    void verifyCode_인증번호_불일치() throws Exception {
        doThrow(new ApplicationException(SmsVerificationErrorType.CODE_MISMATCH))
                .when(smsVerificationService).verifyCode(any(), any());

        mockMvc.perform(post(VERIFY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SmsVerifyRequestDto(PHONE_NUMBER, VALID_CODE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(8004));
    }

    @Test
    @DisplayName("인증 시도 횟수를 초과하면 400을 반환한다")
    void verifyCode_시도_횟수_초과() throws Exception {
        doThrow(new ApplicationException(SmsVerificationErrorType.CODE_MAX_FAIL_EXCEEDED))
                .when(smsVerificationService).verifyCode(any(), any());

        mockMvc.perform(post(VERIFY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SmsVerifyRequestDto(PHONE_NUMBER, VALID_CODE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(8005));
    }
}
