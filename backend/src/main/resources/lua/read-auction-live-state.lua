-- 물품 Hash와 리더보드 상위 3명을 같은 Redis 시점으로 읽는다.
-- KEYS[1] 물품 Hash
-- KEYS[2] 물품 리더보드 ZSET
-- ARGV[1] itemId

local function keyType(key)
    local response = redis.call('TYPE', key)
    if type(response) == 'table' then
        return response['ok']
    end
    return response
end

local function requireHashValue(key, field, label)
    local value = redis.call('HGET', key, field)
    if value == false or value == '' then
        error(label .. ' hash is missing field: ' .. field)
    end
    return value
end

local function requireInteger(value, label)
    if value == false or value == nil or value == ''
            or string.match(value, '^%-?%d+$') == nil then
        error(label .. ' is not an integer')
    end
    return value
end

local itemType = keyType(KEYS[1])
if itemType == 'none' then
    return {}
end
if itemType ~= 'hash' then
    error('auction item key has invalid type: ' .. itemType)
end

local leaderboardType = keyType(KEYS[2])
if leaderboardType ~= 'none' and leaderboardType ~= 'zset' then
    error('auction leaderboard key has invalid type: ' .. leaderboardType)
end

local itemId = requireInteger(ARGV[1], 'auction item.itemId')
local roomId = requireInteger(requireHashValue(KEYS[1], 'roomId', 'auction item'),
        'auction item.roomId')
local status = requireHashValue(KEYS[1], 'status', 'auction item')
local currentPrice = requireInteger(
        requireHashValue(KEYS[1], 'currentPrice', 'auction item'), 'auction item.currentPrice')
local leaderUserId = redis.call('HGET', KEYS[1], 'leaderUserId') or ''
if leaderUserId ~= '' then
    requireInteger(leaderUserId, 'auction item.leaderUserId')
    if leaderboardType ~= 'zset' then
        error('auction leaderboard is missing for current leader')
    end
    local leaderAmount = redis.call('ZSCORE', KEYS[2], leaderUserId)
    if leaderAmount == false or tonumber(leaderAmount) ~= tonumber(currentPrice) then
        error('auction leaderboard current leader does not match item state')
    end
end
local endAt = requireInteger(
        requireHashValue(KEYS[1], 'endAt', 'auction item'), 'auction item.endAt')
local totalExtensionSeconds = requireInteger(
        requireHashValue(KEYS[1], 'totalExtensionSeconds', 'auction item'),
        'auction item.totalExtensionSeconds')
local revision = requireInteger(
        requireHashValue(KEYS[1], 'revision', 'auction item'), 'auction item.revision')
if requireHashValue(KEYS[1], 'leaderboardSeeded', 'auction item') ~= '1' then
    error('auction item leaderboard is not seeded')
end

local participantNicknamesKey = 'auction:room:' .. roomId .. ':participant-nicknames'
if keyType(participantNicknamesKey) ~= 'hash' then
    error('auction participant nicknames key has invalid type')
end

local ranked = {}
if leaderboardType == 'zset' then
    ranked = redis.call('ZREVRANGE', KEYS[2], 0, 2, 'WITHSCORES')
end

local result = {
    itemId,
    roomId,
    status,
    currentPrice,
    leaderUserId,
    endAt,
    totalExtensionSeconds,
    revision,
    tostring(#ranked / 2)
}

for index = 1, #ranked, 2 do
    local bidderUserId = requireInteger(ranked[index], 'auction leaderboard.bidderUserId')
    local amount = requireInteger(ranked[index + 1], 'auction leaderboard.amount')
    local nickname = requireHashValue(
            participantNicknamesKey, bidderUserId, 'auction participant nicknames')
    table.insert(result, bidderUserId)
    table.insert(result, nickname)
    table.insert(result, amount)
end

return result
