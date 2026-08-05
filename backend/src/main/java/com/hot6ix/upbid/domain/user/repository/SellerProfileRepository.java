package com.hot6ix.upbid.domain.user.repository;

import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    Optional<SellerProfile> findByUser_UserIdAndDeletedAtIsNull(Long userId);

    /**
     * 삭제된 프로필까지 포함해 회원의 프로필을 찾는다. <b>{@code deletedAt}으로 거르지 않는
     * 유일한 조회다</b> — 되살릴 행을 찾는 게 목적이라 그렇다. 등록 경로 밖에서는 쓰지 않는다.
     *
     * <p>{@code user_id}에 UNIQUE가 걸려 있어 결과는 늘 0개 또는 1개다.
     */
    Optional<SellerProfile> findByUser_UserId(Long userId);

    Optional<SellerProfile> findBySellerProfileIdAndDeletedAtIsNull(Long sellerProfileId);
}
