-- 참여자 수 서버별 몫을 증감(+1/-1)으로 반영하고, 합계를 계산해 발행한다.
--
-- 절대값을 HSET하는 participant-count-publish.lua와 달리 HINCRBY를 쓴다. 같은 서버에서
-- 거의 동시에 일어난 두 연결 변화가 서로 다른 순서로 Redis에 도착해도(자바 쪽에서 읽은
-- "그 순간의 로컬 카운트"가 실제 발생 순서와 다르게 도착할 수 있다), 증감은 교환법칙이
-- 성립해서 최종 합은 항상 정확하다 — 절대값 방식처럼 더 최신 값이 더 오래된 값에
-- 덮어써지는 역전이 생기지 않는다.
--
-- KEYS[1] 방별 참여자 수 HASH (필드 = 서버 식별자, 값 = 그 서버의 로컬 참여자 수)
-- ARGV[1] 이 서버의 식별자 (필드명)
-- ARGV[2] 증감량 (+1 또는 -1)
-- ARGV[3] 필드 TTL(초) — 죽은 서버 몫을 자동으로 지운다
-- ARGV[4] 발행 채널
-- ARGV[5] HASH 키 전체 TTL(초) — 방이 비정상 종료돼 정리가 안 된 경우의 안전망
-- ARGV[6] 방 ID — 채널이 전역 하나라 메시지 안에 어느 방인지 같이 실어야 한다

-- 1. 이 서버의 몫을 증감한다. 필드가 없었으면 0에서 시작한 것으로 본다(HINCRBY 기본 동작).
--    HINCRBY도 HSET과 마찬가지로 필드 TTL을 지우므로 HEXPIRE를 반드시 다시 건다.
redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2])
redis.call('HEXPIRE', KEYS[1], ARGV[3], 'FIELDS', 1, ARGV[1])
redis.call('EXPIRE', KEYS[1], ARGV[5])

-- 2. 살아있는(TTL 안 지난) 서버들의 몫을 모두 더한다.
local values = redis.call('HVALS', KEYS[1])
local total = 0
for i = 1, #values do
    total = total + tonumber(values[i])
end

-- 3. "방 ID:합계" 형태로 발행한다. id(순차 번호) 없이, 재연결 replay 버퍼에도 안 쌓는다.
redis.call('PUBLISH', ARGV[4], ARGV[6] .. ':' .. tostring(total))

return total
