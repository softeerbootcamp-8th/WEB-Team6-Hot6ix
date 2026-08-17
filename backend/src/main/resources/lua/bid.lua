-- 입찰 판정, Soft Close, 승인 결과 캐시, 영속화 Stream 기록을 원자적으로 수행한다.
--
-- KEYS[1] auction:item:{itemId} Hash
-- KEYS[2] auction:bid:request:{requestId} Hash
-- KEYS[3] auction:bid:stream Stream
-- ARGV[1] requestId
-- ARGV[2] bidderUserId
-- ARGV[3] amount

local requestId = ARGV[1]
local bidderId = ARGV[2]
local amountArgument = ARGV[3]
local itemId = string.match(KEYS[1], '(%d+)$')

local function keyType(key)
    local response = redis.call('TYPE', key)
    if type(response) == 'table' then
        return response['ok']
    end
    return response
end

local function requireKeyType(key, expected, allowMissing, label)
    local actual = keyType(key)
    if actual ~= expected and not (allowMissing and actual == 'none') then
        error(label .. ' key has invalid type: ' .. actual)
    end
    return actual
end

local function requireHashValue(key, field, label)
    local value = redis.call('HGET', key, field)
    if value == false or value == '' then
        error(label .. ' hash is missing field: ' .. field)
    end
    return value
end

local function parseInteger(value, label)
    if value == false or value == nil or value == ''
            or string.match(value, '^%-?%d+$') == nil then
        error(label .. ' is not an integer')
    end
    return tonumber(value)
end

local function requireHashInteger(key, field, label)
    return parseInteger(requireHashValue(key, field, label), label .. '.' .. field)
end

local function optionalHashInteger(key, field, label)
    local value = redis.call('HGET', key, field)
    if value == false or value == '' then
        return nil
    end
    return parseInteger(value, label .. '.' .. field)
end

local requestKeyType = requireKeyType(KEYS[2], 'hash', true, 'bid request')
if requestKeyType == 'hash' then
    local cachedItemId = requireHashValue(KEYS[2], 'itemId', 'bid request')
    local cachedBidderId = requireHashValue(KEYS[2], 'bidderUserId', 'bid request')
    local cachedAmount = requireHashValue(KEYS[2], 'amount', 'bid request')
    local cachedRoomId = requireHashValue(KEYS[2], 'roomId', 'bid request')
    local cachedItemName = requireHashValue(KEYS[2], 'itemName', 'bid request')
    local cachedBidderNickname = requireHashValue(KEYS[2], 'bidderNickname', 'bid request')
    local cachedAcceptedAt = requireHashValue(KEYS[2], 'acceptedAt', 'bid request')
    local cachedEndAt = requireHashValue(KEYS[2], 'endAt', 'bid request')
    local cachedExtendedSeconds = requireHashValue(KEYS[2], 'extendedSeconds', 'bid request')
    parseInteger(cachedItemId, 'bid request.itemId')
    parseInteger(cachedBidderId, 'bid request.bidderUserId')
    parseInteger(cachedAmount, 'bid request.amount')
    parseInteger(cachedRoomId, 'bid request.roomId')
    parseInteger(cachedAcceptedAt, 'bid request.acceptedAt')
    parseInteger(cachedEndAt, 'bid request.endAt')
    parseInteger(cachedExtendedSeconds, 'bid request.extendedSeconds')

    if cachedItemId ~= itemId
            or cachedBidderId ~= bidderId
            or cachedAmount ~= amountArgument then
        return {'REJECTED', 'IDEMPOTENCY_KEY_CONFLICT'}
    end
    return {'ACCEPTED', requestId,
        cachedRoomId,
        cachedItemName,
        cachedBidderId,
        cachedBidderNickname,
        cachedAmount,
        cachedAcceptedAt,
        cachedEndAt,
        cachedExtendedSeconds,
        '1'}
end

local itemKeyType = requireKeyType(KEYS[1], 'hash', true, 'auction item')
if itemKeyType == 'none' then
    return {'REJECTED', 'KEY_MISSING'}
end

requireKeyType(KEYS[3], 'stream', true, 'bid stream')

local status = requireHashValue(KEYS[1], 'status', 'auction item')
local sellerUserId = requireHashValue(KEYS[1], 'sellerUserId', 'auction item')
local roomId = requireHashValue(KEYS[1], 'roomId', 'auction item')
local itemName = requireHashValue(KEYS[1], 'itemName', 'auction item')
local startingPrice = requireHashInteger(KEYS[1], 'startingPrice', 'auction item')
local currentPrice = requireHashInteger(KEYS[1], 'currentPrice', 'auction item')
local bidIncrement = requireHashInteger(KEYS[1], 'bidIncrement', 'auction item')
local endAt = requireHashInteger(KEYS[1], 'endAt', 'auction item')
local trigger = optionalHashInteger(KEYS[1], 'softCloseTriggerSeconds', 'auction item')
local extend = optionalHashInteger(KEYS[1], 'softCloseExtendSeconds', 'auction item')
local total = requireHashInteger(KEYS[1], 'totalExtensionSeconds', 'auction item')
local maximum = requireHashInteger(KEYS[1], 'maxTotalExtensionSeconds', 'auction item')
local leaderUserId = redis.call('HGET', KEYS[1], 'leaderUserId')
local amount = parseInteger(amountArgument, 'bid amount')
local redisTime = redis.call('TIME')
local decisionAt = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)

if bidIncrement <= 0 then
    error('auction item.bidIncrement must be positive')
end

local participantsKey = 'auction:room:' .. roomId .. ':participants'
local participantNicknamesKey = 'auction:room:' .. roomId .. ':participant-nicknames'
requireKeyType(participantsKey, 'set', true, 'auction participants')
requireKeyType(participantNicknamesKey, 'hash', true, 'auction participant nicknames')

if status ~= 'IN_PROGRESS' then
    return {'REJECTED', 'ITEM_NOT_IN_PROGRESS'}
end

if sellerUserId == bidderId then
    return {'REJECTED', 'SELLER_CANNOT_BID'}
end

if redis.call('SISMEMBER', participantsKey, bidderId) == 0 then
    return {'REJECTED', 'TERMS_NOT_AGREED'}
end

local bidderNickname = requireHashValue(
        participantNicknamesKey, bidderId, 'auction participant nicknames')

if decisionAt >= endAt then
    return {'REJECTED', 'ITEM_CLOSED'}
end

if leaderUserId == bidderId then
    return {'REJECTED', 'ALREADY_TOP_BIDDER'}
end

local minimum
if leaderUserId == false then
    minimum = startingPrice
else
    minimum = currentPrice + bidIncrement
end

if amount < minimum then
    return {'REJECTED', 'BID_AMOUNT_TOO_LOW'}
end
if (amount - startingPrice) % bidIncrement ~= 0 then
    return {'REJECTED', 'INVALID_BID_UNIT'}
end

local extendedSeconds = 0
local acceptedAt = decisionAt

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
        'roomId', roomId,
        'itemName', itemName,
        'bidderUserId', bidderId,
        'bidderNickname', bidderNickname,
        'amount', amountString,
        'acceptedAt', acceptedAtString,
        'endAt', endAtString,
        'extendedSeconds', extendedString)

return {'ACCEPTED', requestId,
    roomId,
    itemName,
    bidderId,
    bidderNickname,
    amountString,
    acceptedAtString,
    endAtString,
    extendedString,
    '0'}
