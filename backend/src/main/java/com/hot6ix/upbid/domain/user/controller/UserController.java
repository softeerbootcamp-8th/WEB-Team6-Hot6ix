package com.hot6ix.upbid.domain.user.controller;

import com.hot6ix.upbid.domain.user.api.UserApi;
import com.hot6ix.upbid.domain.user.dto.response.UserMeResponseDto;
import com.hot6ix.upbid.domain.user.service.UserService;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<CommonResponse<UserMeResponseDto>> getMe(@LoginUserId Long userId) {

        UserMeResponseDto response = userService.getMe(userId);

        return ResponseEntity.ok(CommonResponse.ok(response, "내 정보 조회에 성공했습니다."));
    }
}
