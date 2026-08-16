-- 참여자 수 서버별 몫을 갱신하고, 합계를 계산해 발행한다.
--
-- KEYS[1] 방별 참여자 수 HASH (필드 = 서버 식별자, 값 = 그 서버의 로컬 참여자 수)
-- ARGV[1] 이 서버의 식별자 (필드명)
-- ARGV[2] 이 서버의 현재 로컬 참여자 수
-- ARGV[3] 필드 TTL(초) — 죽은 서버 몫을 자동으로 지운다
-- ARGV[4] 발행 채널
-- ARGV[5] HASH 키 전체 TTL(초) — 방이 비정상 종료돼 정리가 안 된 경우의 안전망

-- 1. 이 서버의 몫을 갱신한다. HSET은 필드 TTL을 지우므로 HEXPIRE를 반드시 다시 건다.
redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
redis.call('HEXPIRE', KEYS[1], ARGV[3], 'FIELDS', 1, ARGV[1])
redis.call('EXPIRE', KEYS[1], ARGV[5])

-- 2. 살아있는(TTL 안 지난) 서버들의 몫을 모두 더한다.
local values = redis.call('HVALS', KEYS[1])
local total = 0
for i = 1, #values do
    total = total + tonumber(values[i])
end

-- 3. id 없이 합계만 발행한다. 재연결 replay 버퍼에는 안 쌓는다 — 참여자 수는 최신 값만
--    의미 있는 신호라, 버퍼 자리를 차지하면 정작 replay가 필요한 입찰·마감 이벤트가 밀려난다.
redis.call('PUBLISH', ARGV[4], tostring(total))

return total
