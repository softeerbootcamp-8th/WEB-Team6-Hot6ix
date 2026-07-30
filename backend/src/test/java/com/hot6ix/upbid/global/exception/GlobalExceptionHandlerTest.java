package com.hot6ix.upbid.global.exception;

import com.hot6ix.upbid.global.session.SessionManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandlerTest.TestController.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @MockitoBean
    private SessionManager sessionManager;

    @Autowired
    private MockMvc mockMvc;

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

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/application")
        public void applicationException() {
            throw new ApplicationException(TestErrorType.TEST_EXCEPTION);
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
