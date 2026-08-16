-- 경매 상태 Hash가 기대 스냅샷과 같을 때만 SSE 이벤트를 발행한다.
--
-- KEYS[1] 경매 상태 Hash
-- KEYS[2] 방별 순차 ID 카운터
-- KEYS[3] replay 버퍼 ZSET
-- ARGV[1] 발행 채널
-- ARGV[2] 메시지 본문 JSON
-- ARGV[3] replay 버퍼 크기
-- ARGV[4] 키 TTL(초)
-- ARGV[5..] 기대하는 Hash field/value 쌍

local function keyType(key)
    local response = redis.call('TYPE', key)
    if type(response) == 'table' then
        return response['ok']
    end
    return response
end

local stateType = keyType(KEYS[1])
if stateType ~= 'hash' then
    error('auction state key has invalid type: ' .. stateType)
end

local sequenceType = keyType(KEYS[2])
if sequenceType ~= 'none' and sequenceType ~= 'string' then
    error('sse sequence key has invalid type: ' .. sequenceType)
end

local eventsType = keyType(KEYS[3])
if eventsType ~= 'none' and eventsType ~= 'zset' then
    error('sse events key has invalid type: ' .. eventsType)
end

if (#ARGV - 4) % 2 ~= 0 then
    error('expected hash state must be field/value pairs')
end

for index = 5, #ARGV, 2 do
    local actual = redis.call('HGET', KEYS[1], ARGV[index])
    if actual == false then
        actual = ''
    end
    if actual ~= ARGV[index + 1] then
        return 0
    end
end

local id = redis.call('INCR', KEYS[2])
local message = id .. '\n' .. ARGV[2]

redis.call('ZADD', KEYS[3], id, message)
redis.call('ZREMRANGEBYRANK', KEYS[3], 0, -1 - tonumber(ARGV[3]))
redis.call('PUBLISH', ARGV[1], message)
redis.call('EXPIRE', KEYS[2], ARGV[4])
redis.call('EXPIRE', KEYS[3], ARGV[4])

return id
