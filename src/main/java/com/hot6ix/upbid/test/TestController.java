package com.hot6ix.upbid.test;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Test", description = "테스트 API")
@RestController
@RequestMapping("/api/v1/test")
public class TestController {

    @Operation(
            summary = "서버 상태 확인",
            description = "서버가 정상적으로 동작하는지 확인합니다."
    )
    @GetMapping
    public ResponseEntity<TestDto> test() {
        return ResponseEntity.ok(
                new TestDto("UpBid Server is running")
        );
    }
}