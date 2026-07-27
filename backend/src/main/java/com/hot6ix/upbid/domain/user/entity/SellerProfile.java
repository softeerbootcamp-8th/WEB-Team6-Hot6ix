package com.hot6ix.upbid.domain.user.entity;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seller_profile_id")
    private Long sellerProfileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "store_name", length = 30)
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
}
