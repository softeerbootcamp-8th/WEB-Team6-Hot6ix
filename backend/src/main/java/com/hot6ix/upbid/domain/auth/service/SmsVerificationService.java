package com.hot6ix.upbid.domain.auth.service;

import com.hot6ix.upbid.domain.auth.exception.SmsVerificationErrorType;
import com.hot6ix.upbid.domain.auth.sms.naversens.NaverSmsClient;
import com.hot6ix.upbid.domain.auth.sms.store.VerificationCodeStore;
import com.hot6ix.upbid.domain.auth.sms.store.VerificationEntry;
import com.hot6ix.upbid.global.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SmsVerificationService {

    /**
     * 인증번호 실제 만료 시간 (분).
     * UI에 표시되는 제한 시간(3분)보다 1분 길게 설정해 SMS 도착 지연에 대응한다.
     */
    private static final int CODE_EXPIRE_MINUTES = 4;

    /** 재발송 쿨다운 (분). 사용자가 연속으로 재발송 버튼을 누르는 것을 방지한다. */
    private static final int COOLDOWN_MINUTES = 1;

    /** 인증 실패 허용 횟수. 초과 시 인증번호를 폐기하고 재발급을 요구한다. */
    private static final int MAX_FAIL_COUNT = 5;

    /** 1시간 내 최대 발송 횟수. */
    private static final long MAX_SENDS_PER_HOUR = 5;

    /** 하루 최대 발송 횟수. */
    private static final long MAX_SENDS_PER_DAY = 10;

    /** 서비스 전체 일일 최대 발송 건수. 과금 방어를 위한 총량 제한. */
    private static final long MAX_TOTAL_SENDS_PER_DAY = 500;

    /**
     * 암호학적으로 안전한 난수 생성기.
     * Java 기본 Random은 seed 기반이라 예측 가능성이 있어 보안 목적에 부적합하다.
     * SecureRandom은 OS 엔트로피 소스를 사용해 예측이 어렵다.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final NaverSmsClient naverSmsClient;
    private final VerificationCodeStore codeStore;

    /**
     * 인증번호를 생성해 SMS로 발송한다.
     *
     * <p>처리 순서:
     * <ol>
     *   <li>발송 횟수 제한 검증 (쿨다운, 시간당, 일당)</li>
     *   <li>6자리 인증번호 생성</li>
     *   <li>SENS API 호출 — 실패 시 예외를 던지고 저장하지 않는다</li>
     *   <li>발송 성공 후 store에 저장 (기존 인증번호 덮어쓰기)</li>
     *   <li>발송 이력 기록</li>
     * </ol>
     */
    public void sendCode(String phoneNumber) {
        validateSendRateLimit(phoneNumber);

        String code = generateCode();
        LocalDateTime now = LocalDateTime.now();
        VerificationEntry entry = new VerificationEntry(code, now, now.plusMinutes(CODE_EXPIRE_MINUTES), 0);

        naverSmsClient.sendVerificationCode(phoneNumber, code);

        codeStore.save(phoneNumber, entry);
        codeStore.recordSend(phoneNumber);
    }

    /**
     * 입력한 인증번호가 발급된 번호와 일치하는지 검증한다.
     *
     * <p>검증 순서:
     * <ol>
     *   <li>인증번호 존재 여부</li>
     *   <li>만료 여부 — 만료 시 즉시 삭제</li>
     *   <li>실패 횟수 초과 여부 — 이미 5회 초과 상태면 삭제 (정상 흐름에서는 도달하지 않는 안전망)</li>
     *   <li>코드 일치 여부 — 불일치 시 실패 횟수 증가, 5회째 실패면 즉시 삭제</li>
     *   <li>인증 성공 — 즉시 삭제</li>
     * </ol>
     */
    public void verifyCode(String phoneNumber, String code) {
        VerificationEntry entry = codeStore.find(phoneNumber)
                .orElseThrow(() -> new ApplicationException(SmsVerificationErrorType.CODE_NOT_FOUND));

        if (entry.isExpired()) {
            codeStore.delete(phoneNumber);
            throw new ApplicationException(SmsVerificationErrorType.CODE_EXPIRED);
        }

        if (entry.failCount() >= MAX_FAIL_COUNT) {
            codeStore.delete(phoneNumber);
            throw new ApplicationException(SmsVerificationErrorType.CODE_MAX_FAIL_EXCEEDED);
        }

        if (!entry.code().equals(code)) {
            int newFailCount = entry.failCount() + 1;
            if (newFailCount >= MAX_FAIL_COUNT) {
                // 5번째 실패 — 인증번호 폐기 후 재발급 유도
                codeStore.delete(phoneNumber);
                throw new ApplicationException(SmsVerificationErrorType.CODE_MAX_FAIL_EXCEEDED);
            }
            codeStore.incrementFailCount(phoneNumber);
            throw new ApplicationException(SmsVerificationErrorType.CODE_MISMATCH);
        }

        // 인증 성공 — 재사용 방지를 위해 즉시 삭제
        codeStore.delete(phoneNumber);
    }

    /**
     * 발송 전 횟수 제한을 검증한다.
     *
     * <ul>
     *   <li>쿨다운: 마지막 발급 후 1분이 지나지 않으면 재발송 불가</li>
     *   <li>시간당 제한: 1시간 내 5회 초과 불가</li>
     *   <li>일당 제한: 24시간 내 10회 초과 불가</li>
     * </ul>
     */
    private void validateSendRateLimit(String phoneNumber) {
        codeStore.find(phoneNumber).ifPresent(entry -> {
            if (entry.issuedAt().plusMinutes(COOLDOWN_MINUTES).isAfter(LocalDateTime.now())) {
                throw new ApplicationException(SmsVerificationErrorType.SEND_COOLDOWN);
            }
        });

        if (codeStore.countSendsWithinHour(phoneNumber) >= MAX_SENDS_PER_HOUR) {
            throw new ApplicationException(SmsVerificationErrorType.SEND_HOURLY_LIMIT_EXCEEDED);
        }

        if (codeStore.countSendsWithinDay(phoneNumber) >= MAX_SENDS_PER_DAY) {
            throw new ApplicationException(SmsVerificationErrorType.SEND_DAILY_LIMIT_EXCEEDED);
        }

        if (codeStore.countTotalSendsToday() >= MAX_TOTAL_SENDS_PER_DAY) {
            throw new ApplicationException(SmsVerificationErrorType.DAILY_TOTAL_LIMIT_EXCEEDED);
        }
    }

    /**
     * 100000~999999 범위의 6자리 인증번호를 생성한다.
     * SecureRandom.nextInt(900000)은 0~899999를 반환하므로 100000을 더해 범위를 맞춘다.
     */
    private String generateCode() {
        return String.valueOf(SECURE_RANDOM.nextInt(900000) + 100000);
    }
}
