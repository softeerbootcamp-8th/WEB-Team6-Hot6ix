package com.hot6ix.upbid.domain.auth.service;

import com.hot6ix.upbid.domain.auth.exception.SmsVerificationErrorType;
import com.hot6ix.upbid.domain.auth.sms.coolsms.CoolSmsClient;
import com.hot6ix.upbid.domain.auth.sms.store.VerificationCodeStore;
import com.hot6ix.upbid.domain.auth.sms.store.VerificationEntry;
import com.hot6ix.upbid.global.exception.ApplicationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsVerificationServiceTest {

    @Mock
    private CoolSmsClient coolSmsClient;

    @Mock
    private VerificationCodeStore codeStore;

    @InjectMocks
    private SmsVerificationService smsVerificationService;

    private static final String PHONE_NUMBER = "01012345678";
    private static final String VALID_CODE = "123456";

    private VerificationEntry validEntry() {
        return new VerificationEntry(
                VALID_CODE,
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(2),
                0
        );
    }

    private VerificationEntry expiredEntry() {
        return new VerificationEntry(
                VALID_CODE,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().minusMinutes(1),
                0
        );
    }

    // 30초 전에 발급 → 쿨다운 1분 미경과
    private VerificationEntry cooldownEntry() {
        return new VerificationEntry(
                VALID_CODE,
                LocalDateTime.now().minusSeconds(30),
                LocalDateTime.now().plusMinutes(3),
                0
        );
    }

    private VerificationEntry entryWithFailCount(int failCount) {
        return new VerificationEntry(
                VALID_CODE,
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(2),
                failCount
        );
    }

    // ==================== sendCode ====================

    @Test
    @DisplayName("정상적으로 인증번호를 발송하면 store에 저장하고 발송 이력을 기록한다")
    void sendCode_성공() {
        when(codeStore.find(PHONE_NUMBER)).thenReturn(Optional.empty());
        when(codeStore.countSendsWithinHour(PHONE_NUMBER)).thenReturn(0L);
        when(codeStore.countSendsWithinDay(PHONE_NUMBER)).thenReturn(0L);
        doNothing().when(coolSmsClient).sendVerificationCode(eq(PHONE_NUMBER), any());

        smsVerificationService.sendCode(PHONE_NUMBER);

        verify(coolSmsClient).sendVerificationCode(eq(PHONE_NUMBER), any());
        verify(codeStore).save(eq(PHONE_NUMBER), any());
        verify(codeStore).recordSend(PHONE_NUMBER);
    }

    @Test
    @DisplayName("쿨다운 중 재발송하면 SEND_COOLDOWN 예외가 발생한다")
    void sendCode_쿨다운_중_재발송() {
        when(codeStore.find(PHONE_NUMBER)).thenReturn(Optional.of(cooldownEntry()));

        assertThatThrownBy(() -> smsVerificationService.sendCode(PHONE_NUMBER))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SmsVerificationErrorType.SEND_COOLDOWN);

        verify(coolSmsClient, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("1시간 내 5회 초과 발송하면 SEND_HOURLY_LIMIT_EXCEEDED 예외가 발생한다")
    void sendCode_시간당_횟수_초과() {
        when(codeStore.find(PHONE_NUMBER)).thenReturn(Optional.empty());
        when(codeStore.countSendsWithinHour(PHONE_NUMBER)).thenReturn(5L);

        assertThatThrownBy(() -> smsVerificationService.sendCode(PHONE_NUMBER))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SmsVerificationErrorType.SEND_HOURLY_LIMIT_EXCEEDED);

        verify(coolSmsClient, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("하루 10회 초과 발송하면 SEND_DAILY_LIMIT_EXCEEDED 예외가 발생한다")
    void sendCode_일일_횟수_초과() {
        when(codeStore.find(PHONE_NUMBER)).thenReturn(Optional.empty());
        when(codeStore.countSendsWithinHour(PHONE_NUMBER)).thenReturn(0L);
        when(codeStore.countSendsWithinDay(PHONE_NUMBER)).thenReturn(10L);

        assertThatThrownBy(() -> smsVerificationService.sendCode(PHONE_NUMBER))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SmsVerificationErrorType.SEND_DAILY_LIMIT_EXCEEDED);

        verify(coolSmsClient, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("SMS 발송이 실패하면 store에 저장하지 않는다")
    void sendCode_SMS_발송_실패_시_저장_안_함() {
        when(codeStore.find(PHONE_NUMBER)).thenReturn(Optional.empty());
        when(codeStore.countSendsWithinHour(PHONE_NUMBER)).thenReturn(0L);
        when(codeStore.countSendsWithinDay(PHONE_NUMBER)).thenReturn(0L);
        doThrow(new ApplicationException(SmsVerificationErrorType.SMS_SEND_FAILED))
                .when(coolSmsClient).sendVerificationCode(eq(PHONE_NUMBER), any());

        assertThatThrownBy(() -> smsVerificationService.sendCode(PHONE_NUMBER))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SmsVerificationErrorType.SMS_SEND_FAILED);

        verify(codeStore, never()).save(any(), any());
        verify(codeStore, never()).recordSend(any());
    }

    // ==================== verifyCode ====================

    @Test
    @DisplayName("올바른 인증번호를 입력하면 인증에 성공하고 엔트리가 삭제된다")
    void verifyCode_성공() {
        when(codeStore.find(PHONE_NUMBER)).thenReturn(Optional.of(validEntry()));

        smsVerificationService.verifyCode(PHONE_NUMBER, VALID_CODE);

        verify(codeStore).delete(PHONE_NUMBER);
    }

    @Test
    @DisplayName("인증번호를 발급받지 않은 번호로 검증하면 CODE_NOT_FOUND 예외가 발생한다")
    void verifyCode_인증번호_없음() {
        when(codeStore.find(PHONE_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> smsVerificationService.verifyCode(PHONE_NUMBER, VALID_CODE))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SmsVerificationErrorType.CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("만료된 인증번호로 검증하면 CODE_EXPIRED 예외가 발생하고 엔트리가 삭제된다")
    void verifyCode_만료된_인증번호() {
        when(codeStore.find(PHONE_NUMBER)).thenReturn(Optional.of(expiredEntry()));

        assertThatThrownBy(() -> smsVerificationService.verifyCode(PHONE_NUMBER, VALID_CODE))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SmsVerificationErrorType.CODE_EXPIRED);

        verify(codeStore).delete(PHONE_NUMBER);
    }

    @Test
    @DisplayName("인증번호가 틀리면 CODE_MISMATCH 예외가 발생하고 실패 횟수가 증가한다")
    void verifyCode_인증번호_불일치() {
        when(codeStore.find(PHONE_NUMBER)).thenReturn(Optional.of(validEntry()));

        assertThatThrownBy(() -> smsVerificationService.verifyCode(PHONE_NUMBER, "000000"))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SmsVerificationErrorType.CODE_MISMATCH);

        verify(codeStore).incrementFailCount(PHONE_NUMBER);
        verify(codeStore, never()).delete(PHONE_NUMBER);
    }

    @Test
    @DisplayName("5번째 실패 시 CODE_MAX_FAIL_EXCEEDED 예외가 발생하고 엔트리가 삭제된다")
    void verifyCode_5번째_실패_시_인증번호_폐기() {
        when(codeStore.find(PHONE_NUMBER)).thenReturn(Optional.of(entryWithFailCount(4)));

        assertThatThrownBy(() -> smsVerificationService.verifyCode(PHONE_NUMBER, "000000"))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SmsVerificationErrorType.CODE_MAX_FAIL_EXCEEDED);

        verify(codeStore).delete(PHONE_NUMBER);
        verify(codeStore, never()).incrementFailCount(any());
    }

    @Test
    @DisplayName("이미 실패 횟수가 최대인 엔트리로 검증하면 CODE_MAX_FAIL_EXCEEDED 예외가 발생하고 엔트리가 삭제된다")
    void verifyCode_실패_횟수_이미_최대() {
        when(codeStore.find(PHONE_NUMBER)).thenReturn(Optional.of(entryWithFailCount(5)));

        assertThatThrownBy(() -> smsVerificationService.verifyCode(PHONE_NUMBER, VALID_CODE))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SmsVerificationErrorType.CODE_MAX_FAIL_EXCEEDED);

        verify(codeStore).delete(PHONE_NUMBER);
    }
}
