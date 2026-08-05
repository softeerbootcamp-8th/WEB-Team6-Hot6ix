package com.hot6ix.upbid.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class UserRepositoryTest extends AbstractMySqlContainerTest {

    @Autowired
    private UserRepository userRepository;

    private User newUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build());
    }

    @Test
    @DisplayName("존재하는 회원이면 조회된다")
    void findByUserIdAndDeletedAtIsNull_found() {

        User user = newUser("seller1@hot6ix.com");

        assertThat(userRepository.findByUserIdAndDeletedAtIsNull(user.getUserId())).isPresent();
    }

    @Test
    @DisplayName("탈퇴(soft delete)한 회원은 조회되지 않는다")
    void findByUserIdAndDeletedAtIsNull_excludesWithdrawn() {

        User user = newUser("seller2@hot6ix.com");
        user.softDelete(LocalDateTime.now());
        userRepository.saveAndFlush(user);

        assertThat(userRepository.findByUserIdAndDeletedAtIsNull(user.getUserId())).isEmpty();
    }

    @Test
    @DisplayName("provider+providerId가 중복되면 DataIntegrityViolationException이 발생한다")
    void save_duplicateProviderAndProviderId_throwsDataIntegrityViolationException() {

        userRepository.saveAndFlush(User.builder()
                .provider(OauthProvider.KAKAO)
                .providerId("kakao-dup")
                .nickname("first")
                .build());

        User duplicate = User.builder()
                .provider(OauthProvider.KAKAO)
                .providerId("kakao-dup")
                .nickname("second")
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
