package com.hot6ix.upbid.domain.user.controller;

import com.hot6ix.upbid.domain.user.api.UserApi;
import com.hot6ix.upbid.domain.user.dto.request.UserUpdateRequestDto;
import com.hot6ix.upbid.domain.user.dto.response.UserResponseDto;
import com.hot6ix.upbid.domain.user.service.UserService;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<CommonResponse<UserResponseDto>> getMe(@LoginUserId Long userId) {

        UserResponseDto response = userService.getMe(userId);

        return ResponseEntity.ok(CommonResponse.ok(response, "내 정보 조회에 성공했습니다."));
    }

    @Override
    @PutMapping("/me")
    public ResponseEntity<CommonResponse<UserResponseDto>> updateMe(
            @LoginUserId Long userId,
            @Valid @RequestBody UserUpdateRequestDto request) {

        UserResponseDto response = userService.updateMe(userId, request);

        return ResponseEntity.ok(CommonResponse.ok(response, "프로필이 수정되었습니다."));
    }
}
