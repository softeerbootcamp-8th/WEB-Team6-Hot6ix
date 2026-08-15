-- 입찰 판정, Soft Close, 승인 결과 캐시, 영속화 Stream 기록을 원자적으로 수행한다.
--
-- KEYS[1] auction:item:{itemId} Hash
-- KEYS[2] auction:bid:request:{requestId} Hash
-- KEYS[3] auction:bid:stream Stream
-- ARGV[1] requestId
-- ARGV[2] bidderUserId
-- ARGV[3] amount
-- ARGV[4] arrivedAt epoch millis

local requestId = ARGV[1]
local bidderId = ARGV[2]
local amountArgument = ARGV[3]
local itemId = string.match(KEYS[1], '(%d+)$')

if redis.call('EXISTS', KEYS[2]) == 1 then
    if redis.call('HGET', KEYS[2], 'itemId') ~= itemId
            or redis.call('HGET', KEYS[2], 'bidderUserId') ~= bidderId
            or redis.call('HGET', KEYS[2], 'amount') ~= amountArgument then
        return {'REJECTED', 'IDEMPOTENCY_KEY_CONFLICT'}
    end
    return {'ACCEPTED', requestId,
        redis.call('HGET', KEYS[2], 'amount'),
        redis.call('HGET', KEYS[2], 'acceptedAt'),
        redis.call('HGET', KEYS[2], 'endAt'),
        redis.call('HGET', KEYS[2], 'extendedSeconds'),
        '1'}
end

if redis.call('EXISTS', KEYS[1]) == 0 then
    return {'REJECTED', 'KEY_MISSING'}
end

if redis.call('HGET', KEYS[1], 'status') ~= 'IN_PROGRESS' then
    return {'REJECTED', 'ITEM_NOT_IN_PROGRESS'}
end

if redis.call('HGET', KEYS[1], 'sellerUserId') == bidderId then
    return {'REJECTED', 'SELLER_CANNOT_BID'}
end

local roomId = redis.call('HGET', KEYS[1], 'roomId')
if redis.call('SISMEMBER', 'auction:room:' .. roomId .. ':participants', bidderId) == 0 then
    return {'REJECTED', 'TERMS_NOT_AGREED'}
end

local endAt = tonumber(redis.call('HGET', KEYS[1], 'endAt'))
if tonumber(ARGV[4]) >= endAt then
    return {'REJECTED', 'ITEM_CLOSED'}
end

local leaderUserId = redis.call('HGET', KEYS[1], 'leaderUserId')
if leaderUserId == bidderId then
    return {'REJECTED', 'ALREADY_TOP_BIDDER'}
end

local amount = tonumber(amountArgument)
local startingPrice = tonumber(redis.call('HGET', KEYS[1], 'startingPrice'))
local bidIncrement = tonumber(redis.call('HGET', KEYS[1], 'bidIncrement'))
local minimum
if leaderUserId == false then
    minimum = startingPrice
else
    minimum = tonumber(redis.call('HGET', KEYS[1], 'currentPrice')) + bidIncrement
end

if amount < minimum then
    return {'REJECTED', 'BID_AMOUNT_TOO_LOW'}
end
if (amount - startingPrice) % bidIncrement ~= 0 then
    return {'REJECTED', 'INVALID_BID_UNIT'}
end

local redisTime = redis.call('TIME')
local acceptedAt = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)
local trigger = tonumber(redis.call('HGET', KEYS[1], 'softCloseTriggerSeconds'))
local extend = tonumber(redis.call('HGET', KEYS[1], 'softCloseExtendSeconds'))
local total = tonumber(redis.call('HGET', KEYS[1], 'totalExtensionSeconds')) or 0
local maximum = tonumber(redis.call('HGET', KEYS[1], 'maxTotalExtensionSeconds')) or 0
local extendedSeconds = 0

if trigger ~= nil and extend ~= nil
        and acceptedAt >= endAt - trigger * 1000
        and total + extend <= maximum then
    endAt = endAt + extend * 1000
    total = total + extend
    extendedSeconds = extend
end

local amountString = string.format('%d', amount)
local endAtString = string.format('%d', endAt)
local acceptedAtString = string.format('%d', acceptedAt)
local totalString = string.format('%d', total)
local extendedString = string.format('%d', extendedSeconds)

redis.call('HSET', KEYS[1],
        'currentPrice', amountString,
        'leaderUserId', bidderId,
        'endAt', endAtString,
        'totalExtensionSeconds', totalString)

redis.call('XADD', KEYS[3], '*',
        'type', 'BID_ACCEPTED',
        'requestId', requestId,
        'itemId', itemId,
        'roomId', roomId,
        'bidderUserId', bidderId,
        'amount', amountString,
        'acceptedAt', acceptedAtString,
        'endAt', endAtString,
        'extendedSeconds', extendedString,
        'totalExtensionSeconds', totalString)

redis.call('HSET', KEYS[2],
        'itemId', itemId,
        'bidderUserId', bidderId,
        'amount', amountString,
        'acceptedAt', acceptedAtString,
        'endAt', endAtString,
        'extendedSeconds', extendedString)

return {'ACCEPTED', requestId, amountString, acceptedAtString, endAtString, extendedString, '0'}
