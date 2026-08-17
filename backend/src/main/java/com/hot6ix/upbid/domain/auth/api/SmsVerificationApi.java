package com.hot6ix.upbid.domain.auth.api;

import com.hot6ix.upbid.domain.auth.dto.request.SmsSendRequestDto;
import com.hot6ix.upbid.domain.auth.dto.request.SmsVerifyRequestDto;
import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "SMS 인증", description = "전화번호 인증 API")
public interface SmsVerificationApi {

    @Operation(
            summary = "인증번호 발송",
            description = "전화번호로 6자리 인증번호를 발송한다. "
                    + "재발송은 마지막 발송 후 1분이 지나야 가능하며, 1시간 최대 5회·하루 최대 10회로 제한된다. "
                    + "새 인증번호 발급 시 기존 인증번호는 즉시 폐기된다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증번호 발송 성공"),
            @ApiResponse(responseCode = "400", description = "올바르지 않은 전화번호 형식 (code 2002)"),
            @ApiResponse(responseCode = "429", description = "재발송 쿨다운 (code 8006) / 시간당 발송 초과 (code 8007) / 일일 발송 초과 (code 8008)"),
            @ApiResponse(responseCode = "502", description = "SMS 발송 실패 (code 8001)")
    })
    @SecurityRequirements
    ResponseEntity<CommonResponse<Void>> sendCode(SmsSendRequestDto request);

    @Operation(
            summary = "인증번호 확인",
            description = "입력한 인증번호가 발급된 번호와 일치하는지 검증한다. "
                    + "인증번호는 발송 후 3분(UI 기준)이 지나면 만료된다. "
                    + "5회 연속 실패 시 인증번호가 폐기되며 재발급이 필요하다. "
                    + "인증 성공 시 인증번호는 즉시 삭제된다. "
                    + "가입 대기 상태(카카오 인증 후 회원가입 전)라면 인증된 전화번호가 세션에 기록되어 "
                    + "이후 회원가입 API에서 사용된다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공"),
            @ApiResponse(responseCode = "400", description = "인증번호 없음 (code 8002) / 만료 (code 8003) / 불일치 (code 8004) / 시도 횟수 초과 (code 8005)")
    })
    ResponseEntity<CommonResponse<Void>> verifyCode(HttpServletRequest httpRequest, SmsVerifyRequestDto request);
}
