package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomShareResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionRoomShareServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SELLER_PROFILE_ID = 7L;
    private static final Long ROOM_ID = 10L;
    private static final String SHARE_CODE = "aBcD1234aBcD1234";

    @Mock
    private AuctionRoomRepository auctionRoomRepository;

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    /** frontendUrl이 조립 로직의 입력이라 테스트마다 다른 값으로 서비스를 만든다. */
    private AuctionRoomShareService newService(String frontendUrl) {
        return new AuctionRoomShareService(auctionRoomRepository, sellerProfileRepository, frontendUrl);
    }

    private SellerProfile newSellerProfile() {
        User user = User.builder()
                .email("seller@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build();

        SellerProfile sellerProfile = SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .build();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", SELLER_PROFILE_ID);

        return sellerProfile;
    }

    private AuctionRoom newAuctionRoom(SellerProfile sellerProfile) {
        AuctionRoom auctionRoom = AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name("승민의 경매방")
                .shareCode(SHARE_CODE)
                .status(AuctionRoomStatus.BEFORE)
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();
        ReflectionTestUtils.setField(auctionRoom, "auctionRoomId", ROOM_ID);

        return auctionRoom;
    }

    private void 소유자의_경매방을_스텁한다() {
        SellerProfile sellerProfile = newSellerProfile();

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(sellerProfile));
        when(auctionRoomRepository.findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, SELLER_PROFILE_ID))
                .thenReturn(Optional.of(newAuctionRoom(sellerProfile)));
    }

    @Test
    @DisplayName("16자 영숫자 코드를 생성한다")
    void generateCandidateShareCode_lengthAndCharset() {

        String code = newService("https://upbid.com").generateCandidateShareCode();

        assertThat(code).hasSize(16);
        assertThat(code).matches("^[0-9A-Za-z]{16}$");
    }

    @Test
    @DisplayName("호출할 때마다 다른 코드를 생성한다")
    void generateCandidateShareCode_differsBetweenCalls() {

        AuctionRoomShareService auctionRoomShareService = newService("https://upbid.com");

        String first = auctionRoomShareService.generateCandidateShareCode();
        String second = auctionRoomShareService.generateCandidateShareCode();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("소유자가 조회하면 프론트 주소와 share_code로 조립한 공유 링크를 반환한다")
    void getShareInfo() {

        소유자의_경매방을_스텁한다();

        AuctionRoomShareResponseDto response = newService("https://upbid.com").getShareInfo(USER_ID, ROOM_ID);

        assertThat(response.shareUrl()).isEqualTo("https://upbid.com/join/" + SHARE_CODE);
    }

    @Test
    @DisplayName("프론트 주소가 슬래시로 끝나도 공유 링크에 슬래시가 겹치지 않는다")
    void getShareInfo_trimsTrailingSlash() {

        소유자의_경매방을_스텁한다();

        AuctionRoomShareResponseDto response = newService("https://upbid.com/").getShareInfo(USER_ID, ROOM_ID);

        assertThat(response.shareUrl()).isEqualTo("https://upbid.com/join/" + SHARE_CODE);
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 공유 링크 조회 시 예외가 발생한다")
    void getShareInfo_sellerProfileNotFound() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService("https://upbid.com").getShareInfo(USER_ID, ROOM_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("경매방이 없거나 본인 소유가 아니면 공유 링크 조회 시 예외가 발생한다")
    void getShareInfo_roomNotFound() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(newSellerProfile()));
        when(auctionRoomRepository.findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, SELLER_PROFILE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService("https://upbid.com").getShareInfo(USER_ID, ROOM_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }
}
