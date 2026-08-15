--[[
  Token Bucket 알고리즘. 버킷 상태(tokens, last_refill)를 해시 하나에 담는다.

  KEYS[1] 버킷 키
  ARGV[1] capacity            버킷 최대 용량
  ARGV[2] refill_rate_per_sec 초당 토큰 충전량
  ARGV[3] requested_tokens    이번 요청이 소모할 토큰 수

  현재 시각은 애플리케이션 서버 시각이 아니라 Redis의 TIME 을 쓴다. 여러 WAS가 각자의
  시계로 now 를 계산해 넘기면 서버 간 clock drift 만큼 토큰이 과다/과소 충전될 수 있는데,
  이 스크립트를 실행하는 Redis 노드는 항상 하나이므로 TIME 을 기준으로 삼으면 그 문제가
  사라진다.

  반환: 1(허용) 또는 0(거절)
]]

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

local redis_time = redis.call('TIME')
local now = (tonumber(redis_time[1]) * 1000) + math.floor(tonumber(redis_time[2]) / 1000)

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if not tokens then
    tokens = capacity
    last_refill = now
else
    local elapsed_ms = math.max(0, now - last_refill)
    local refill = math.floor((elapsed_ms / 1000) * refill_rate)

    -- refill 이 0 이어도 시각을 전진시키면 그 사이의 소수점 이하 토큰이 매번 버려져
    -- 장기적으로 리필이 느려진다. 그래서 실제로 채울 토큰이 있을 때만 갱신한다.
    if refill > 0 then
        tokens = math.min(capacity, tokens + refill)
        last_refill = now
    end
end

-- 버킷이 가득 차는 시간이 지나면 그 뒤로는 값을 들고 있을 이유가 없다.
local ttl = math.ceil(capacity / refill_rate)

if tokens >= requested then
    tokens = tokens - requested
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
    redis.call('EXPIRE', key, ttl)
    return 1
end

-- 거절 시에는 상태를 쓰지 않는다. 다음 요청이 last_refill 기준으로 경과 시간을 다시
-- 계산하면 이번에 놓친 리필까지 포함되므로 결과는 같고, 연타/DoS 상황에서 매 거절마다
-- Redis 쓰기가 발생하는 것만 막을 수 있다.
return 0
