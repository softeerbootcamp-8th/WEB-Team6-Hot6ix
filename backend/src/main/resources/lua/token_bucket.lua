--[[
  Token Bucket 알고리즘. 버킷 상태(tokens, last_refill)를 해시 하나에 담는다.

  KEYS[1] 버킷 키
  ARGV[1] capacity            버킷 최대 용량
  ARGV[2] refill_rate_per_sec 초당 토큰 충전량
  ARGV[3] requested_tokens    이번 요청이 소모할 토큰 수
  ARGV[4] now_ms              현재 시각(ms). Redis 서버 시각이 아니라 애플리케이션이 넘겨준
                              값을 써야 여러 서버 간 시간 편차에서 자유롭다

  반환: 1(허용) 또는 0(거절)
]]

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

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

-- 거절해도 리필 결과는 반영해 둔다. 안 그러면 다음 요청이 지금 계산한 리필을 다시 놓친다.
redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
redis.call('EXPIRE', key, ttl)

return 0
