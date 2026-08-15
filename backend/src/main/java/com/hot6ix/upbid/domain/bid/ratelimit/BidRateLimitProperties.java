package com.hot6ix.upbid.domain.bid.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 입찰 Token Bucket 파라미터.
 *
 * <p>버킷 크기·리필 속도는 확정값이 아니라 제안값이다. 실사용 로그의 사용자별 req/s 분포로
 * 재조정할 것이므로 상수가 아니라 프로퍼티로 둔다.
 *
 * @param enabled          꺼두면 항상 허용한다(운영 중 리미터만 급히 내려야 할 때 대비)
 * @param capacity         버킷 최대 용량. 소프트클로즈 구간의 연타를 흡수하는 여유분이다
 * @param refillPerSecond  초당 리필되는 토큰 수. 지속적인 폭주를 자르는 값이다
 */
@ConfigurationProperties(prefix = "upbid.rate-limit.bid")
public record BidRateLimitProperties(
        boolean enabled,
        int capacity,
        int refillPerSecond
) {
}
