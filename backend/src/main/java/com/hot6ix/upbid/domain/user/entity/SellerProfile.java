package com.hot6ix.upbid.domain.user.entity;

import com.hot6ix.upbid.domain.user.dto.request.SellerProfileCreateRequestDto;
import com.hot6ix.upbid.domain.user.dto.request.SellerProfileUpdateRequestDto;
import com.hot6ix.upbid.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "seller_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerProfile extends BaseEntity {

    /** 회원탈퇴 시 storePhoneNumber에 채우는 값. 실제 연락처를 지우면서도 화면에는 빈 줄 대신 이유를 보여준다. */
    private static final String WITHDRAWN_STORE_PHONE_NUMBER = "탈퇴한 가게";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seller_profile_id")
    private Long sellerProfileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(name = "store_name", length = 30, nullable = false)
    private String storeName;

    @Column(name = "store_image_url", columnDefinition = "TEXT")
    private String storeImageUrl;

    @Column(name = "sns_url", columnDefinition = "TEXT")
    private String snsUrl;

    @Column(name = "store_phone_number", length = 30)
    private String storePhoneNumber;

    @Column(name = "store_description", length = 100)
    private String storeDescription;

    @Builder
    private SellerProfile(User user, String storeName, String storeImageUrl, String snsUrl,
                          String storePhoneNumber, String storeDescription) {
        this.user = user;
        this.storeName = storeName;
        this.storeImageUrl = storeImageUrl;
        this.snsUrl = snsUrl;
        this.storePhoneNumber = storePhoneNumber;
        this.storeDescription = storeDescription;
    }

    public static SellerProfile from(User user, SellerProfileCreateRequestDto request) {
        return SellerProfile.builder()
                .user(user)
                .storeName(request.storeName())
                .storeImageUrl(request.storeImageUrl())
                .snsUrl(request.snsUrl())
                .storePhoneNumber(request.storePhoneNumber())
                .storeDescription(request.storeDescription())
                .build();
    }

    /**
     * 삭제됐던 프로필을 등록 요청 값으로 되살린다. 새 행을 넣는 대신 이 행을 되살리므로
     * {@code seller_profile_id}가 유지되고, 이 값을 가리키던 경매방·상품·거래가 그대로 붙는다.
     */
    public void restore(SellerProfileCreateRequestDto request) {
        super.restore();
        this.storeName = request.storeName();
        this.storeImageUrl = request.storeImageUrl();
        this.snsUrl = request.snsUrl();
        this.storePhoneNumber = request.storePhoneNumber();
        this.storeDescription = request.storeDescription();
    }

    public void update(SellerProfileUpdateRequestDto request) {
        this.storeName = request.storeName();
        this.storeImageUrl = request.storeImageUrl();
        this.snsUrl = request.snsUrl();
        this.storePhoneNumber = request.storePhoneNumber();
        this.storeDescription = request.storeDescription();
    }

    /**
     * 회원탈퇴 시 연락처를 "탈퇴한 가게"로 바꾼다. soft delete는 조회 경로를 막아 주지만
     * DB에는 값이 그대로 남으므로, 실제 연락처는 지우고 화면에는 그 이유를 남긴다.
     * storeName 등 나머지 필드는 낙찰 결과·거래 내역처럼 이력으로 남는 화면과 무관해
     * 그대로 둔다.
     */
    public void withdrawStorePhoneNumber() {
        this.storePhoneNumber = WITHDRAWN_STORE_PHONE_NUMBER;
    }
}
