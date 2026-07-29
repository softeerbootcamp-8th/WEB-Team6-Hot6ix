package com.hot6ix.upbid.domain.user.repository;

import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    Optional<SellerProfile> findByUser_UserIdAndDeletedAtIsNull(Long userId);

    boolean existsByUser_UserIdAndDeletedAtIsNull(Long userId);
}
