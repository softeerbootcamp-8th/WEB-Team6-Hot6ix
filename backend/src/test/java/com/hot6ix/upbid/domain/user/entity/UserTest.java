package com.hot6ix.upbid.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

    private User newUser(Long userId) {
        User user = User.builder()
                .provider(OauthProvider.KAKAO)
                .providerId("kakao-12345")
                .email("user@hot6ix.com")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .profileImageUrl("https://cdn.hot6ix.com/profile.png")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    @Test
    @DisplayName("withdraw()는 provider·phoneNumber는 남기고, providerId·email·nickname은 userId를 붙인 "
            + "값으로, profileImageUrl은 null로 바꾸고 지금 시각으로 soft delete한다")
    void withdraw_anonymizesAndSoftDeletes() {

        User user = newUser(42L);
        LocalDateTime before = LocalDateTime.now();

        user.withdraw();

        LocalDateTime after = LocalDateTime.now();
        assertThat(user.getProvider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(user.getProviderId()).isEqualTo("withdrawn_42");
        assertThat(user.getEmail()).isEqualTo("withdrawn_42");
        assertThat(user.getNickname()).isEqualTo("탈퇴한 회원42");
        assertThat(user.getPhoneNumber()).isEqualTo("010-1234-5678");
        assertThat(user.getProfileImageUrl()).isNull();
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isBetween(before, after);
    }
}
