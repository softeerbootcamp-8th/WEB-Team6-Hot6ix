package com.hot6ix.upbid.domain.user.repository;

import com.hot6ix.upbid.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
