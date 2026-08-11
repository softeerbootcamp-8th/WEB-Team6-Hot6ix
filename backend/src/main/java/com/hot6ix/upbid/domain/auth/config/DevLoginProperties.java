package com.hot6ix.upbid.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 배포 서버에서 {@code dev-login} 을 잠깐 열 때 쓰는 값 (이슈 #266).
 *
 * <p>부하 측정은 {@code dev-login} 으로 시딩과 로그인을 하는데 그게 운영에는 없어서, 배포에
 * 부하를 걸려면 측정 창구에만 열어야 한다. 그렇다고 프로파일에 {@code prod} 를 더하면
 * 인터넷에 그냥 열리므로, 토큰을 아는 요청만 통과시킨다.
 *
 * @param token 비어 있으면 게이트를 안 건다. {@code local} 과 {@code perf} 는 이 상태로 두어
 *              지금처럼 헤더 없이 부른다. 값이 있으면 헤더가 일치할 때만 세션을 발급한다
 */
@ConfigurationProperties(prefix = "upbid.dev-login")
public record DevLoginProperties(String token) {
}
