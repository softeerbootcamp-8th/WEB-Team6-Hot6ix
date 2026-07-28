package com.hot6ix.upbid.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.config.JpaConfig;
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
class SellerProfileRepositoryTest {

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

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

    private SellerProfile newSellerProfile(User user) {
        return SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .snsUrl("https://instagram.com/hot6ix")
                .build();
    }

    @Test
    @DisplayName("같은 user_id로 두 번째 SellerProfile을 저장하면 유니크 제약 위반이 발생한다")
    void uniqueConstraint_violated_onDuplicateUserId() {

        User user = newUser("seller1@hot6ix.com");
        sellerProfileRepository.saveAndFlush(newSellerProfile(user));

        SellerProfile duplicate = newSellerProfile(user);

        assertThatThrownBy(() -> sellerProfileRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("storeName 없이 저장하면 예외가 발생한다")
    void storeName_notNull_violated() {

        User user = newUser("seller3@hot6ix.com");
        SellerProfile sellerProfile = SellerProfile.builder()
                .user(user)
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .snsUrl("https://instagram.com/hot6ix")
                .build();

        assertThatThrownBy(() -> sellerProfileRepository.saveAndFlush(sellerProfile))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("user 없이 저장하면 예외가 발생한다")
    void user_notNull_violated() {

        SellerProfile sellerProfile = SellerProfile.builder()
                .storeName("승민상점")
                .storeImageUrl("https://cdn.hot6ix.com/store.png")
                .snsUrl("https://instagram.com/hot6ix")
                .build();

        assertThatThrownBy(() -> sellerProfileRepository.saveAndFlush(sellerProfile))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("필드를 수정하고 flush하면 updatedAt이 실제로 갱신된다")
    void updatedAt_refreshed_afterFlush() throws InterruptedException {

        User user = newUser("seller2@hot6ix.com");
        SellerProfile sellerProfile = sellerProfileRepository.saveAndFlush(newSellerProfile(user));

        LocalDateTime before = sellerProfile.getUpdatedAt();
        Thread.sleep(10);

        sellerProfile.update("새로운상점", null, null, null, null);
        sellerProfileRepository.flush();

        assertThat(sellerProfile.getUpdatedAt()).isAfter(before);
    }
}
