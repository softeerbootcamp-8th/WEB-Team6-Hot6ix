package com.hot6ix.upbid.global.redis;

import java.time.LocalDateTime;

/**
 * {@link RedisDelayQueue#claimDue}가 집어온 예약 하나.
 *
 * @param id           예약 대상의 ID
 * @param scheduledFor <b>집기 전에 걸려 있던</b> 실행 시각. 집으면서 뒤로 미루기 때문에 이
 *                     값을 여기 담아 주지 않으면 실행이 얼마나 늦었는지 잴 수 없다
 */
public record DueEntry(Long id, LocalDateTime scheduledFor) {
}
