package com.hot6ix.upbid.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.user.dto.request.SellerProfileCreateRequestDto;
import com.hot6ix.upbid.domain.user.dto.request.SellerProfileUpdateRequestDto;
import com.hot6ix.upbid.domain.user.dto.response.SellerProfileResponseDto;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.service.SellerProfileService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = SellerProfileController.class)
@Import(GlobalExceptionHandler.class)
class SellerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SellerProfileService sellerProfileService;

    private SellerProfileResponseDto sampleResponse() {
        return new SellerProfileResponseDto(
                1L, "승민상점", "https://cdn.hot6ix.com/store.png",
                "https://instagram.com/hot6ix", "02-1234-5678", "안녕하세요",
                null, null
        );
    }

    @Test
    @DisplayName("판매자 프로필을 등록하면 201과 등록된 정보를 반환한다")
    void create() throws Exception {

        SellerProfileCreateRequestDto request = new SellerProfileCreateRequestDto(
                "승민상점", "https://cdn.hot6ix.com/store.png", "https://instagram.com/hot6ix",
                "02-1234-5678", "안녕하세요"
        );

        when(sellerProfileService.create(eq(1L), any(SellerProfileCreateRequestDto.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/seller-profiles")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeName").value("승민상점"));
    }

    @Test
    @DisplayName("가게 이름 형식이 올바르지 않으면 등록 시 400을 반환한다")
    void create_invalidStoreName() throws Exception {

        SellerProfileCreateRequestDto request = new SellerProfileCreateRequestDto(
                "이", "https://cdn.hot6ix.com/store.png", "https://instagram.com/hot6ix",
                null, null
        );

        mockMvc.perform(post("/api/v1/seller-profiles")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("storeName"));
    }

    @Test
    @DisplayName("내 판매자 프로필을 조회한다")
    void getMyProfile() throws Exception {

        when(sellerProfileService.getMyProfile(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/seller-profiles/me").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeName").value("승민상점"));
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 조회 시 404와 에러코드를 반환한다")
    void getMyProfile_notFound() throws Exception {

        when(sellerProfileService.getMyProfile(1L))
                .thenThrow(new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/seller-profiles/me").header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(3002));
    }

    @Test
    @DisplayName("판매자 프로필의 일부 필드를 수정한다")
    void update() throws Exception {

        SellerProfileUpdateRequestDto request = new SellerProfileUpdateRequestDto(
                "새로운상점", null, null, null, null
        );

        when(sellerProfileService.update(eq(1L), any(SellerProfileUpdateRequestDto.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/seller-profiles/me")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("판매자 프로필을 삭제한다")
    void deleteMyProfile() throws Exception {

        mockMvc.perform(delete("/api/v1/seller-profiles/me").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
