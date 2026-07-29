package com.hot6ix.upbid.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.user.dto.request.SellerProfileCreateRequestDto;
import com.hot6ix.upbid.domain.user.dto.request.SellerProfileUpdateRequestDto;
import com.hot6ix.upbid.domain.user.dto.response.SellerProfileResponseDto;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SellerProfileServiceTest {

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SellerProfileService sellerProfileService;

    private User newUser() {
        return User.builder()
                .email("seller@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build();
    }

    private SellerProfile newSellerProfile(User user) {
        return SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .snsUrl("https://instagram.com/hot6ix")
                .storePhoneNumber("02-1234-5678")
                .storeDescription("기존 소개")
                .build();
    }

    @Test
    @DisplayName("판매자 프로필을 등록한다")
    void create() {

        User user = newUser();
        SellerProfileCreateRequestDto request = SellerProfileCreateRequestDto.builder()
                .storeName("승민상점")
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .snsUrl("https://instagram.com/hot6ix")
                .storePhoneNumber("02-1234-5678")
                .storeDescription("안녕하세요")
                .build();

        when(sellerProfileRepository.existsByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sellerProfileRepository.saveAndFlush(any(SellerProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SellerProfileResponseDto response = sellerProfileService.create(1L, request);

        assertThat(response.storeName()).isEqualTo("승민상점");
        assertThat(response.snsUrl()).isEqualTo("https://instagram.com/hot6ix");
        verify(sellerProfileRepository, times(1)).saveAndFlush(any(SellerProfile.class));
    }

    @Test
    @DisplayName("동시 요청으로 유니크 제약을 위반하면 등록 시 예외가 발생한다")
    void create_raceCondition() {

        User user = newUser();
        SellerProfileCreateRequestDto request = SellerProfileCreateRequestDto.builder()
                .storeName("승민상점")
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .snsUrl("https://instagram.com/hot6ix")
                .build();

        when(sellerProfileRepository.existsByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sellerProfileRepository.saveAndFlush(any(SellerProfile.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violated"));

        assertThatThrownBy(() -> sellerProfileService.create(1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.DUPLICATE_SELLER_PROFILE);
    }

    @Test
    @DisplayName("이미 등록된 판매자 프로필이 있으면 등록 시 예외가 발생한다")
    void create_duplicate() {

        SellerProfileCreateRequestDto request = SellerProfileCreateRequestDto.builder()
                .storeName("승민상점")
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .snsUrl("https://instagram.com/hot6ix")
                .build();

        when(sellerProfileRepository.existsByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(true);

        assertThatThrownBy(() -> sellerProfileService.create(1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.DUPLICATE_SELLER_PROFILE);

        verify(userRepository, never()).findById(any());
        verify(sellerProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 등록 시 예외가 발생한다")
    void create_userNotFound() {

        SellerProfileCreateRequestDto request = SellerProfileCreateRequestDto.builder()
                .storeName("승민상점")
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .snsUrl("https://instagram.com/hot6ix")
                .build();

        when(sellerProfileRepository.existsByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerProfileService.create(1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(CommonErrorType.RESOURCE_NOT_FOUND);

        verify(sellerProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("내 판매자 프로필을 조회한다")
    void getMyProfile() {

        SellerProfile sellerProfile = newSellerProfile(newUser());

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));

        SellerProfileResponseDto response = sellerProfileService.getMyProfile(1L);

        assertThat(response.storeName()).isEqualTo("승민상점");
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 조회 시 예외가 발생한다")
    void getMyProfile_notFound() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerProfileService.getMyProfile(1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("요청 값으로 프로필 전체가 교체된다")
    void update_fullReplace() {

        SellerProfile sellerProfile = newSellerProfile(newUser());
        SellerProfileUpdateRequestDto request = SellerProfileUpdateRequestDto.builder()
                .storeName("새로운상점")
                .storeImageUrl("https://cdn.hot6ix.com/new-store.png")
                .snsUrl("https://youtube.com/@newstore")
                .storePhoneNumber("02-9999-9999")
                .storeDescription("새로운 소개")
                .build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));

        SellerProfileResponseDto response = sellerProfileService.update(1L, request);

        assertThat(response.storeName()).isEqualTo("새로운상점");
        assertThat(response.storeImageUrl()).isEqualTo("https://cdn.hot6ix.com/new-store.png");
        assertThat(response.snsUrl()).isEqualTo("https://youtube.com/@newstore");
        assertThat(response.storePhoneNumber()).isEqualTo("02-9999-9999");
        assertThat(response.storeDescription()).isEqualTo("새로운 소개");
    }

    @Test
    @DisplayName("생략된 선택 필드는 null로 지워진다")
    void update_clearsOmittedOptionalFields() {

        SellerProfile sellerProfile = newSellerProfile(newUser());
        SellerProfileUpdateRequestDto request = SellerProfileUpdateRequestDto.builder()
                .storeName("새로운상점")
                .build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));

        SellerProfileResponseDto response = sellerProfileService.update(1L, request);

        assertThat(response.storeName()).isEqualTo("새로운상점");
        assertThat(response.storeImageUrl()).isNull();
        assertThat(response.snsUrl()).isNull();
        assertThat(response.storePhoneNumber()).isNull();
        assertThat(response.storeDescription()).isNull();
    }

    @Test
    @DisplayName("수정 대상 프로필이 없으면 예외가 발생한다")
    void update_notFound() {

        SellerProfileUpdateRequestDto request = SellerProfileUpdateRequestDto.builder()
                .storeName("새로운상점")
                .build();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerProfileService.update(1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("판매자 프로필을 삭제하면 soft delete 된다")
    void delete() {

        SellerProfile sellerProfile = newSellerProfile(newUser());

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(sellerProfile));

        sellerProfileService.delete(1L);

        assertThat(sellerProfile.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제 대상 프로필이 없으면 예외가 발생한다")
    void delete_notFound() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerProfileService.delete(1L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }
}
