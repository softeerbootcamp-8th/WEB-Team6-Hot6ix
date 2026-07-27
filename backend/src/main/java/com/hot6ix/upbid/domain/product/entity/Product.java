package com.hot6ix.upbid.domain.product.entity;

import com.hot6ix.upbid.domain.user.entity.SellerProfile;
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
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_profile_id")
    private SellerProfile sellerProfile;

    @Column(name = "name", length = 30)
    private String name;

    @Column(name = "description", length = 100)
    private String description;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "reference_url", columnDefinition = "TEXT")
    private String referenceUrl;

    @Builder
    private Product(SellerProfile sellerProfile, String name, String description,
                    String imageUrl, String referenceUrl) {
        this.sellerProfile = sellerProfile;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.referenceUrl = referenceUrl;
    }
}
