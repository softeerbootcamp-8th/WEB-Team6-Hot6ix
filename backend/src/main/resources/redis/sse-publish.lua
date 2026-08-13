-- SSE 이벤트 하나를 발행한다.
--
-- KEYS[1] 순차 ID 카운터
-- KEYS[2] replay 버퍼 (ZSET, score = ID)
-- ARGV[1] 발행 채널
-- ARGV[2] 메시지 본문 JSON (ID 없음 — ID 는 아래에서 정해져 앞에 붙는다)
-- ARGV[3] 버퍼에 남길 이벤트 수
-- ARGV[4] 키 TTL(초)

-- 1. 이벤트 ID를 발급하고 SSE 이벤트 문자열 생성
local id = redis.call('INCR', KEYS[1])
-- INCR를 통해 만든 id와 message를 더함
local message = id .. '\n' .. ARGV[2]

-- 2. EventBuffer(ZSET)에 이벤트 저장
-- score(id) 기준으로 정렬되므로 Last-Event-ID 이후 이벤트를 쉽게 조회할 수 있다.
redis.call('ZADD', KEYS[2], id, message)

-- 최신 N개만 유지한다.
-- 버퍼 크기를 초과한 오래된 이벤트는 제거한다.
redis.call('ZREMRANGEBYRANK', KEYS[2], 0, -1 - tonumber(ARGV[3]))

-- 3. Redis Pub/Sub 채널에 이벤트 발행
redis.call('PUBLISH', ARGV[1], message)
-- 이벤트가 계속 발생하는 방은 살아있도록 TTL 갱신
redis.call('EXPIRE', KEYS[1], ARGV[4])
redis.call('EXPIRE', KEYS[2], ARGV[4])

return id
