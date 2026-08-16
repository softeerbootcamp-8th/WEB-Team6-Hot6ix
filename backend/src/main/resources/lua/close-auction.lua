-- 자연 마감 또는 판매자 마감 앞당기기와 Stream 기록을 한 번에 수행한다.
-- KEYS[1] item Hash, KEYS[2] bid/closing Stream
-- ARGV[1] NATURAL | SELLER_ADVANCE
-- ARGV[2] NATURAL이면 poll 시각 epoch millis, SELLER_ADVANCE면 요청 userId
-- ARGV[3] SELLER_ADVANCE에서 마감까지 남길 초. 비면 연장 트리거 초 (#336)

if redis.call('EXISTS', KEYS[1]) == 0 then
    return {'REJECTED', 'KEY_MISSING', ''}
end

local endAtString = redis.call('HGET', KEYS[1], 'endAt')
if redis.call('HGET', KEYS[1], 'status') ~= 'IN_PROGRESS' then
    return {'REJECTED', 'ITEM_NOT_IN_PROGRESS', endAtString or ''}
end

local mode = ARGV[1]
local roomId = redis.call('HGET', KEYS[1], 'roomId')
local itemId = string.match(KEYS[1], '(%d+)$')

if mode == 'SELLER_ADVANCE' then
    if redis.call('HGET', KEYS[1], 'sellerUserId') ~= ARGV[2] then
        return {'REJECTED', 'NOT_OWNER', endAtString}
    end

    local redisTime = redis.call('TIME')
    local advancedAt = tonumber(redisTime[1]) * 1000
            + math.floor(tonumber(redisTime[2]) / 1000)
    local trigger = tonumber(redis.call('HGET', KEYS[1], 'softCloseTriggerSeconds')) or 60
    local remaining = tonumber(ARGV[3]) or trigger

    -- 트리거 초로도 마감이 뒤로 밀리면 앞당길 자리 자체가 없다. 요청 값과 무관한
    -- 물품 상태의 문제라, 아래 두 거절과 갈라야 화면에서 다르게 안내할 수 있다.
    if advancedAt + trigger * 1000 >= tonumber(endAtString) then
        return {'REJECTED', 'ALREADY_CLOSING_SOON', endAtString}
    end

    -- 트리거보다 짧게 남기면 앞당기는 즉시 연장 구간이라 알릴 순간이 없다.
    if remaining < trigger then
        return {'REJECTED', 'REMAINING_TOO_SHORT', endAtString}
    end

    local advancedEndAt = advancedAt + remaining * 1000

    -- 막지 않으면 앞당기기가 오히려 마감을 미루는 셈이 된다.
    if advancedEndAt >= tonumber(endAtString) then
        return {'REJECTED', 'REMAINING_TOO_LONG', endAtString}
    end

    local advancedAtResult = string.format('%d', advancedAt)
    local advancedEndAtResult = string.format('%d', advancedEndAt)
    local remainingResult = string.format('%d', remaining)
    redis.call('HSET', KEYS[1], 'endAt', advancedEndAtResult)
    redis.call('XADD', KEYS[2], '*',
            'type', 'ITEM_CLOSE_ADVANCED',
            'itemId', itemId,
            'roomId', roomId,
            'endAt', advancedEndAtResult,
            'remainingSeconds', remainingResult,
            'advancedAt', advancedAtResult)
    return {'ADVANCED', advancedEndAtResult, remainingResult, advancedAtResult}
end

if mode ~= 'NATURAL' then
    error('unsupported close mode: ' .. mode)
end

local closedAt = tonumber(ARGV[2])
if closedAt < tonumber(endAtString) then
    return {'REJECTED', 'NOT_DUE', endAtString}
end

local closedAtString = string.format('%d', closedAt)
local currentPrice = redis.call('HGET', KEYS[1], 'currentPrice')
local leaderUserId = redis.call('HGET', KEYS[1], 'leaderUserId') or ''
local totalExtensionSeconds = redis.call('HGET', KEYS[1], 'totalExtensionSeconds') or '0'

redis.call('HSET', KEYS[1], 'status', 'CLOSING')
redis.call('XADD', KEYS[2], '*',
        'type', 'ITEM_CLOSING',
        'itemId', itemId,
        'roomId', roomId,
        'currentPrice', currentPrice,
        'leaderUserId', leaderUserId,
        'endAt', endAtString,
        'totalExtensionSeconds', totalExtensionSeconds,
        'closeReason', 'NATURAL',
        'closedAt', closedAtString)

return {'CLOSING', closedAtString}
