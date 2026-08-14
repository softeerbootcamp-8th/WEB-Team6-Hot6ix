package com.hot6ix.upbid.global.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.auth.domain.PendingSignup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 세션 값 직렬화기의 폴리모픽 타입 설정({@code enableUnsafeDefaultTyping()})이 실제로
 * {@code PendingSignup} 같은 커스텀 타입을 복원해내는지 확인한다. 이 설정이 빠지면
 * {@code PendingSignup}은 {@code LinkedHashMap}으로 돌아와 {@code PendingSignupManager}에서
 * {@code ClassCastException}이 터진다.
 */
class SessionSerializationConfigTest {

    private final RedisSerializer<Object> serializer =
            new SessionSerializationConfig().springSessionDefaultRedisSerializer();

    @Test
    @DisplayName("PendingSignup을 직렬화·역직렬화하면 원래 타입과 값 그대로 복원된다")
    void roundTripsPendingSignup() {

        PendingSignup pendingSignup = new PendingSignup(
                OauthProvider.KAKAO, "provider-id", "user@hot6ix.com", "승민", null);

        byte[] bytes = serializer.serialize(pendingSignup);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(PendingSignup.class).isEqualTo(pendingSignup);
    }

    @Test
    @DisplayName("Long은 natural type이라 타입 정보가 안 붙어서 Integer로 복원된다")
    void roundTripsLongAsInteger() {

        // 이 직렬화기는 String·Boolean·Integer·Long 같은 natural type엔 @class를 안 붙인다
        // (Jackson 자체 로직). 그래서 Long으로 저장해도 Object로 역직렬화하면 Integer로
        // 돌아온다 — SessionManager.findUserId()가 (Long) 대신 (Number)+longValue()로
        // 받아야 하는 이유가 바로 이거다. 이 테스트가 실패하면(즉 Long으로 복원되게 바뀌면)
        // 그 방어 코드가 더 필요 없어진 것이니 같이 재검토해야 한다.
        Long userId = 1L;

        byte[] bytes = serializer.serialize(userId);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(Integer.class).isEqualTo(userId.intValue());
    }
}
