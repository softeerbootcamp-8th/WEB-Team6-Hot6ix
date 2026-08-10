package com.hot6ix.upbid.domain.auth.service;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile({"local", "perf"})
@Service
@RequiredArgsConstructor
public class DevAuthService {

    /** key 없이 부를 때 쓰는 회원. 프론트 개발용 로그인이 지금까지 쓰던 그 회원이다. */
    private static final String DEFAULT_KEY = "test-user";

    private static final String PROVIDER_ID_PREFIX = "dev-";

    /** {@code users.provider_id} 컬럼 길이(100)에서 접두사를 뺀 값. */
    private static final int MAX_KEY_LENGTH = 96;

    /** {@code users.nickname} 컬럼 길이. */
    private static final int MAX_NICKNAME_LENGTH = 20;

    private final UserRepository userRepository;

    /**
     * 테스트용 회원을 찾거나 만들고 그 ID를 돌려준다.
     *
     * <p><b>{@code key}가 곧 회원 하나다.</b> 같은 key로 다시 부르면 같은 회원이 나오고, 다른
     * key로 부르면 다른 회원이 생긴다. 부하 측정에서 가상 사용자 N명을 서로 다른 회원으로
     * 만들려고 열어 둔 값이다 — key가 없으면 전원이 같은 회원이 되어 두 번째 입찰부터
     * {@code ALREADY_TOP_BIDDER}로 거절되고, 그러면 입찰 부하를 아예 걸 수 없다.
     *
     * <p>key를 생략하면 예전과 같은 회원({@code dev-test-user})이 나온다. 프론트 개발용
     * 로그인이 파라미터 없이 부르고 있어서, 그 동작을 그대로 둔다.
     *
     * <p><b>같은 key로 동시에 두 번 부르면 유니크 제약에 걸려 실패할 수 있다.</b> 막지 않은
     * 것은 부하 스크립트가 VU 번호를 key로 써서 겹치지 않고, 회원은 {@code seed.sh}가
     * 미리 만들어 두기 때문이다.
     *
     * @param key 회원을 가르는 값. {@code null}이거나 공백이면 기본 회원
     * @return 찾았거나 새로 만든 회원의 ID
     */
    @Transactional
    public Long findOrCreateDevUser(String key) {

        String normalized = normalize(key);
        String providerId = PROVIDER_ID_PREFIX + normalized;

        return userRepository.findByProviderAndProviderId(OauthProvider.DEV, providerId)
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(OauthProvider.DEV)
                        .providerId(providerId)
                        .nickname(nicknameOf(normalized))
                        .build()))
                .getUserId();
    }

    private String normalize(String key) {
        if (key == null || key.isBlank()) {
            return DEFAULT_KEY;
        }

        String trimmed = key.trim();

        return trimmed.length() <= MAX_KEY_LENGTH ? trimmed : trimmed.substring(0, MAX_KEY_LENGTH);
    }

    private String nicknameOf(String normalizedKey) {
        if (DEFAULT_KEY.equals(normalizedKey)) {
            return "테스트유저";
        }

        return normalizedKey.length() <= MAX_NICKNAME_LENGTH
                ? normalizedKey
                : normalizedKey.substring(0, MAX_NICKNAME_LENGTH);
    }
}
