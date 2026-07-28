package com.hot6ix.upbid.domain.user.entity;

import com.hot6ix.upbid.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_provider_id",
                columnNames = "provider_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "nickname", length = 10)
    private String nickname;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    @Builder
    private User(String providerId, String email, String password,
                 String nickname, String phoneNumber, String profileImageUrl) {
        this.providerId = providerId;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 소셜 로그인 최초 진입 시 회원을 생성한다.
     * 닉네임은 OAuth에서 내려오지 않으므로 이후 온보딩에서 입력받는다.
     */
    public static User ofOAuth(String providerId, String email, String phoneNumber) {
        return User.builder()
                .providerId(providerId)
                .email(email)
                .phoneNumber(phoneNumber)
                .build();
    }
}
