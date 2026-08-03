package com.hot6ix.upbid.domain.auth.service;

import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("local")
@Service
@RequiredArgsConstructor
public class DevAuthService {

    private static final String DEV_PROVIDER = "dev";
    private static final String DEV_PROVIDER_ID = "dev-test-user";

    private final UserRepository userRepository;

    @Transactional
    public Long findOrCreateDevUser() {

        return userRepository.findByProviderId(DEV_PROVIDER_ID)
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(DEV_PROVIDER)
                        .providerId(DEV_PROVIDER_ID)
                        .nickname("테스트유저")
                        .build()))
                .getUserId();
    }
}
