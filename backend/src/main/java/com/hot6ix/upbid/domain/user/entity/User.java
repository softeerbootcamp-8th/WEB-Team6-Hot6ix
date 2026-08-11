package com.hot6ix.upbid.domain.user.entity;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.auth.domain.PendingSignup;
import com.hot6ix.upbid.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_provider_provider_id",
                columnNames = {"provider", "provider_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    /** 탈퇴 시 providerId·email에 붙이는 접두사. userId를 그대로 붙여 유일성을 보장한다. */
    private static final String WITHDRAWN_PREFIX = "withdrawn_";

    /** 탈퇴 시 nickname에 붙이는 접두사. userId를 그대로 붙여 다른 탈퇴 회원과 겹치지 않게 한다. */
    private static final String WITHDRAWN_NICKNAME_PREFIX = "탈퇴한 회원";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20)
    private OauthProvider provider;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "nickname", length = 20)
    private String nickname;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    @Builder
    private User(OauthProvider provider, String providerId, String email, String password,
                 String nickname, String phoneNumber, String profileImageUrl) {
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.profileImageUrl = profileImageUrl;
    }

    public static User ofPendingSignup(PendingSignup pendingSignup) {
        return User.builder()
                .provider(pendingSignup.provider())
                .providerId(pendingSignup.providerId())
                .nickname(pendingSignup.nickname())
                .email(pendingSignup.email())
                .phoneNumber(pendingSignup.verifiedPhoneNumber())
                .build();
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 회원탈퇴 시 개인정보를 익명화하고 soft delete한다.
     *
     * <p>{@code provider}와 {@code phoneNumber}는 남긴다. {@code provider}는 개인정보가
     * 아니고, {@code phoneNumber}를 읽는 경로({@code DealCandidateResponseDto}) 는 이미
     * 탈퇴 회원을 걸러내는 쿼리를 쓰고 있어 익명화해도 얻는 이득이 없다.
     *
     * <p>{@code providerId}/{@code email}/{@code nickname} 모두 userId를 그대로 붙인다.
     * {@code providerId}는 UNIQUE 제약을 지키면서 재가입 시 실제 카카오 providerId와
     * 더는 일치하지 않게 하는 게 목적이다. {@code nickname}은 낙찰 결과·거래 내역처럼
     * 제3자 화면에 그대로 노출되는 값이지만, userId를 쓰면 다른 탈퇴 회원과 절대
     * 겹치지 않는다는 확실한 이점이 있어 택했다.
     */
    public void withdraw() {
        this.providerId = WITHDRAWN_PREFIX + userId;
        this.email = WITHDRAWN_PREFIX + userId;
        this.nickname = WITHDRAWN_NICKNAME_PREFIX + userId;
        this.profileImageUrl = null;
        softDelete(LocalDateTime.now());
    }
}
