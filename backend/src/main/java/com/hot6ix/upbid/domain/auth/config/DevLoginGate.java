package com.hot6ix.upbid.domain.auth.config;

import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * {@code dev-login} 요청이 토큰을 제대로 가져왔는지 본다.
 *
 * <p>토큰을 안 걸어 둔 실행에서는 그냥 통과시킨다. {@code local} 과 {@code perf} 가 그 상태이고,
 * 그래야 프론트 DEV 패널과 {@code dev-console.html}, {@code seed.sh}, k6 가 지금처럼 헤더 없이
 * 부를 수 있다. 배포에서만 값을 줘서 게이트를 건다.
 *
 * <p><b>이것만으로 막지 않는다.</b> nginx 도 이 경로를 함께 제한하고, 측정이 끝나면 환경변수를
 * 지운다. {@code DevPageGuard} 가 같은 이유로 방어선을 둘 두고 있다.
 */
@Component
@Conditional(DevLoginCondition.class)
@EnableConfigurationProperties(DevLoginProperties.class)
public class DevLoginGate {

    /** 요청에 토큰을 실어 보내는 헤더. */
    public static final String HEADER = "X-Dev-Login-Token";

    private final byte[] expected;

    public DevLoginGate(DevLoginProperties properties) {
        this.expected = StringUtils.hasText(properties.token())
                ? properties.token().getBytes(StandardCharsets.UTF_8)
                : null;
    }

    /**
     * 토큰을 걸어 둔 실행이면 값이 맞는지 확인한다.
     *
     * <p>{@code MessageDigest.isEqual} 을 쓰는 것은 길이가 다른 값도 안전하게 비교하고
     * 앞자리부터 맞춰 보는 시도에 걸리는 시간이 달라지지 않게 하기 위해서다.
     *
     * @param provided 요청이 가져온 헤더 값. 없으면 {@code null}
     * @throws ApplicationException 토큰을 걸어 뒀는데 값이 없거나 다를 때
     */
    public void verify(String provided) {

        if (expected == null) {
            return;
        }

        byte[] actual = provided == null
                ? new byte[0]
                : provided.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ApplicationException(AuthErrorType.UNAUTHORIZED);
        }
    }
}
