package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
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
class AuctionRoomServiceTest {

    @Mock
    private AuctionRoomRepository auctionRoomRepository;

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    @InjectMocks
    private AuctionRoomService auctionRoomService;

    private SellerProfile newSellerProfile() {
        User user = User.builder()
                .email("seller@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build();

        return SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .build();
    }

    private AuctionRoomCreateRequestDto newCreateRequest() {
        return AuctionRoomCreateRequestDto.builder()
                .name("승민의 경매방")
                .coverImageUrl("https://cdn.hot6ix.com/cover.png")
                .description("한정판 피규어 경매")
                .liveUrl("https://instagram.com/hot6ix")
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();
    }

    @Test
    @DisplayName("경매방을 생성하면 BEFORE 상태로 share_code와 함께 저장된다")
    void create() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoomCreateRequestDto request = newCreateRequest();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionRoomResponseDto response = auctionRoomService.create(1L, request);

        assertThat(response.name()).isEqualTo("승민의 경매방");
        assertThat(response.status()).isEqualTo(AuctionRoomStatus.BEFORE);
        assertThat(response.sellerStoreName()).isEqualTo("승민상점");
        assertThat(response.itemCount()).isNull();
        assertThat(response.participantCount()).isNull();
        verify(auctionRoomRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("share_code가 충돌하면 재시도해서 생성에 성공한다")
    void create_retriesOnShareCodeCollision() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoomCreateRequestDto request = newCreateRequest();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("share_code unique constraint violated"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuctionRoomResponseDto response = auctionRoomService.create(1L, request);

        assertThat(response.name()).isEqualTo("승민의 경매방");
        verify(auctionRoomRepository, times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName("share_code 충돌이 반복해서 재시도 한도를 넘으면 예외가 발생한다")
    void create_exhaustsShareCodeRetries() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoomCreateRequestDto request = newCreateRequest();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("share_code unique constraint violated"));

        assertThatThrownBy(() -> auctionRoomService.create(1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(CommonErrorType.INTERNAL_SERVER_ERROR);

        verify(auctionRoomRepository, times(5)).saveAndFlush(any());
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 생성 시 예외가 발생한다")
    void create_sellerProfileNotFound() {

        AuctionRoomCreateRequestDto request = newCreateRequest();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionRoomService.create(1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);

        verify(auctionRoomRepository, times(0)).saveAndFlush(any());
    }
}
