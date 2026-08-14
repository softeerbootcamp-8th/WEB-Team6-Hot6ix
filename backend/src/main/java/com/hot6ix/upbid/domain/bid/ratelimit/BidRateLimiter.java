package com.hot6ix.upbid.domain.bid.ratelimit;

/**
 * 입찰 요청의 초당 처리량을 사용자 단위로 제한한다.
 *
 * <p>허용 여부가 아니라 <b>거절 여부</b>를 묻는다. Mockito 등 목(mock)의 기본 boolean
 * 반환값이 {@code false}이므로, 이 방향으로 두면 리미터를 스텁하지 않은 테스트가 자동으로
 * "제한 없음"을 뜻하게 되어 그대로 통과한다.
 */
public interface BidRateLimiter {

    boolean isRateLimited(long userId);
}
