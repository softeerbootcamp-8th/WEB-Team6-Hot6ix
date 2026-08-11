package com.hot6ix.upbid.global.exception;

import com.hot6ix.upbid.global.support.AbstractControllerTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandlerTest.TestController.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest extends AbstractControllerTest {

    @Test
    @DisplayName("ApplicationException 발생 시 에러 응답을 반환한다")
    void applicationException() throws Exception {

        mockMvc.perform(get("/test/application"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("테스트 예외입니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    /*
     * SSE 요청은 본문 없이 상태 코드만 받는다. 200 이 나가면 EventSource 가 "정상 연결 후 끊김"
     * 으로 보고 retry 간격마다 무한히 재접속한다.
     */
    @Test
    @DisplayName("SSE 요청에서 ApplicationException 이 나면 본문 없이 상태 코드만 반환한다")
    void applicationExceptionOnEventStream() throws Exception {

        mockMvc.perform(get("/test/application")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isConflict())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("SSE 요청에서 예상치 못한 예외가 나면 본문 없이 500 만 반환한다")
    void unexpectedExceptionOnEventStream() throws Exception {

        mockMvc.perform(get("/test/unexpected")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("RequestBody 검증 실패 시 Validation 에러 응답을 반환한다")
    void bodyValidationException() throws Exception {

        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorType.INVALID_REQUEST.getErrorCode()))
                .andExpect(jsonPath("$.message").value(CommonErrorType.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("이름은 필수 값입니다."));
    }

    @Test
    @DisplayName("RequestParam 검증 실패 시 Validation 에러 응답을 반환한다")
    void paramValidationException() throws Exception {

        mockMvc.perform(post("/test/param")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorType.INVALID_REQUEST.getErrorCode()))
                .andExpect(jsonPath("$.message").value(CommonErrorType.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].message").value("페이지 번호는 1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("PathVariable 검증 실패 시 Validation 에러 응답을 반환한다")
    void pathValidationException() throws Exception {

        mockMvc.perform(post("/test/path/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorType.INVALID_REQUEST.getErrorCode()))
                .andExpect(jsonPath("$.message").value(CommonErrorType.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.errors[0].field").value("pathId"))
                .andExpect(jsonPath("$.errors[0].message").value("path id는 양수여야 합니다."));
    }

    @Test
    @DisplayName("경로 변수 타입이 맞지 않으면 400 에러 응답을 반환한다")
    void pathVariableTypeMismatch() throws Exception {

        mockMvc.perform(get("/test/path/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorType.INVALID_REQUEST.getErrorCode()))
                .andExpect(jsonPath("$.message").value(CommonErrorType.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/application")
        public void applicationException() {
            throw new ApplicationException(TestErrorType.TEST_EXCEPTION);
        }

        @GetMapping("/unexpected")
        public void unexpectedException() {
            throw new IllegalStateException("예상치 못한 예외");
        }

        @PostMapping("/body")
        public void bodyValidationException(@Valid @RequestBody TestRequestDto requestDto) {
        }

        @PostMapping("/param")
        public void paramValidationException(
                @RequestParam @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.") int page) {
        }

        @PostMapping("/path/{pathId}")
        public void pathValidationException(
                @PathVariable @Positive(message = "path id는 양수여야 합니다.") Long pathId) {
        }

        @GetMapping("/path/{pathId}")
        public void pathTypeMismatch(@PathVariable Long pathId) {
        }
    }

    @Getter
    @RequiredArgsConstructor
    enum TestErrorType implements ErrorType {

        TEST_EXCEPTION(HttpStatus.CONFLICT, 1001, "테스트 예외입니다.");

        private final HttpStatus httpStatus;
        private final Integer errorCode;
        private final String message;
    }

    record TestRequestDto(
            @NotBlank(message = "이름은 필수 값입니다.") String name
    ) {
    }
}
