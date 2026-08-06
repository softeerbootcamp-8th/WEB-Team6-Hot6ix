package com.hot6ix.upbid.domain.auth.controller;

import com.hot6ix.upbid.domain.auth.api.SmsVerificationApi;
import com.hot6ix.upbid.domain.auth.dto.request.SmsSendRequestDto;
import com.hot6ix.upbid.domain.auth.dto.request.SmsVerifyRequestDto;
import com.hot6ix.upbid.domain.auth.service.AuthService;
import com.hot6ix.upbid.domain.auth.service.SmsVerificationService;
import com.hot6ix.upbid.global.interceptor.GuestAllowed;
import com.hot6ix.upbid.global.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/phone")
@RequiredArgsConstructor
public class SmsVerificationController implements SmsVerificationApi {

    private final SmsVerificationService smsVerificationService;
    private final AuthService authService;

    @Override
    @GuestAllowed
    @PostMapping("/send")
    public ResponseEntity<CommonResponse<Void>> sendCode(@Valid @RequestBody SmsSendRequestDto request) {
        smsVerificationService.sendCode(request.phoneNumber());
        return ResponseEntity.ok(CommonResponse.ok("인증번호가 발송되었습니다."));
    }

    @Override
    @GuestAllowed
    @PostMapping("/verify")
    public ResponseEntity<CommonResponse<Void>> verifyCode(
            HttpServletRequest httpRequest,
            @Valid @RequestBody SmsVerifyRequestDto request) {

        authService.verifyPhone(httpRequest, request.phoneNumber(), request.code());

        return ResponseEntity.ok(CommonResponse.ok("인증이 완료되었습니다."));
    }
}
