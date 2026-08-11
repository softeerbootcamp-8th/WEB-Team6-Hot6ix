package com.hot6ix.upbid.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.auth.domain.PendingSignup;
import com.hot6ix.upbid.domain.upload.ImageUrlValidator;
import com.hot6ix.upbid.domain.user.dto.request.SellerProfileCreateRequestDto;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 회원탈퇴 후 같은 카카오 계정으로 재가입하는 흐름을 실제 DB로 검증한다.
 *
 * <p>재가입은 완전히 새로운 {@code user_id}를 발급한다 — 탈퇴로 끊긴 이력을 다시 잇지
 * 않는다는 설계다. 그래서 SellerProfile의 {@code restore()}(같은 user_id로 재등록할 때
 * 쓰는 경로)는 여기서 타지 않는다. 이 테스트는 그 사실을 명시적으로 확인한다 — 탈퇴 전
 * 판매자였던 사람도 재가입 후에는 새로 등록해야 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, UserService.class, SellerProfileService.class})
class UserWithdrawIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    // create()/update() 전용 검증이라 여기서는 통과시킨다.
    @MockitoBean
    private ImageUrlValidator imageUrlValidator;

    private User saveUser(String providerId) {
        return userRepository.saveAndFlush(User.builder()
                .provider(OauthProvider.KAKAO)
                .providerId(providerId)
                .nickname("승민")
                .email("seller@hot6ix.com")
                .phoneNumber("010-1234-5678")
                .build());
    }

    @Test
    @DisplayName("탈퇴 후 같은 providerId로 재가입하면 새 user_id가 발급되고, 탈퇴한 계정은 익명화된 채로 남는다")
    void withdraw_thenResignup_createsNewUser() {

        User original = saveUser("kakao-123");
        Long originalId = original.getUserId();

        userService.withdraw(originalId);
        // 실제로는 탈퇴와 재가입이 서로 다른 트랜잭션(다른 요청)이라 탈퇴의 UPDATE가 먼저
        // 커밋된다. 한 트랜잭션에서 이어서 실행하면 Hibernate가 flush 시 INSERT를 UPDATE보다
        // 먼저 내보내 순서가 뒤바뀌므로, 명시적으로 flush해서 그 순서를 강제한다.
        userRepository.flush();

        PendingSignup pendingSignup = new PendingSignup(
                OauthProvider.KAKAO, "kakao-123", "seller@hot6ix.com", "승민", "010-1234-5678");
        Long newUserId = userService.create(pendingSignup);

        assertThat(newUserId).isNotEqualTo(originalId);

        User reloadedOriginal = userRepository.findById(originalId).orElseThrow();
        assertThat(reloadedOriginal.isDeleted()).isTrue();
        assertThat(reloadedOriginal.getProviderId()).isEqualTo("withdrawn_" + originalId);

        User newUser = userRepository.findById(newUserId).orElseThrow();
        assertThat(newUser.isDeleted()).isFalse();
        assertThat(newUser.getProviderId()).isEqualTo("kakao-123");
    }

    @Test
    @DisplayName("판매자였던 회원이 탈퇴 후 재가입하면, 이전 SellerProfile은 새 계정에 되살아나지 않는다")
    void withdraw_thenResignup_doesNotRestoreOldSellerProfile() {

        User original = saveUser("kakao-456");
        Long originalId = original.getUserId();

        SellerProfile sellerProfile = sellerProfileRepository.saveAndFlush(
                SellerProfile.from(original, SellerProfileCreateRequestDto.builder()
                        .storeName("승민상점")
                        .storePhoneNumber("02-1234-5678")
                        .build()));
        Long sellerProfileId = sellerProfile.getSellerProfileId();

        userService.withdraw(originalId);
        // 위 테스트와 같은 이유로, 탈퇴의 UPDATE가 재가입의 INSERT보다 먼저 나가도록 flush한다.
        userRepository.flush();

        PendingSignup pendingSignup = new PendingSignup(
                OauthProvider.KAKAO, "kakao-456", "seller@hot6ix.com", "승민", "010-1234-5678");
        Long newUserId = userService.create(pendingSignup);

        assertThat(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(newUserId)).isEmpty();

        SellerProfile reloadedProfile = sellerProfileRepository.findById(sellerProfileId).orElseThrow();
        assertThat(reloadedProfile.isDeleted()).isTrue();
        assertThat(reloadedProfile.getStorePhoneNumber()).isEqualTo("탈퇴한 가게");
    }
}
