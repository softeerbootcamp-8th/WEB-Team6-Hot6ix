-- KEYS[1] 물품 Hash
-- KEYS[2] 방 참여자 Set
-- KEYS[3] 방 참여자 nickname Hash
--
-- ARGV[1..13] 물품의 고정 필드 값(itemName 포함)
-- ARGV[14..]   약관에 동의한 참여자의 userId, nickname 쌍
--
local function keyType(key)
    local response = redis.call('TYPE', key)
    if type(response) == 'table' then
        return response['ok']
    end
    return response
end

local itemType = keyType(KEYS[1])
if itemType ~= 'none' and itemType ~= 'hash' then
    error('auction item key has invalid type: ' .. itemType)
end

local participantsType = keyType(KEYS[2])
if participantsType ~= 'none' and participantsType ~= 'set' then
    error('auction participants key has invalid type: ' .. participantsType)
end

local nicknamesType = keyType(KEYS[3])
if nicknamesType ~= 'none' and nicknamesType ~= 'hash' then
    error('auction participant nicknames key has invalid type: ' .. nicknamesType)
end

if ARGV[13] == nil or ARGV[13] == '' then
    error('auction item itemName is missing')
end

if (#ARGV - 13) % 2 ~= 0 then
    error('auction participant metadata must be userId/nickname pairs')
end

-- 입찰 Lua가 물품 Hash를 발견했을 때 참여자 ID와 nickname이 모두 준비돼 있도록 먼저 쓴다.
for index = 14, #ARGV, 2 do
    if ARGV[index + 1] == '' then
        error('auction participant nickname is missing')
    end
    redis.call('HSET', KEYS[3], ARGV[index], ARGV[index + 1])
    redis.call('SADD', KEYS[2], ARGV[index])
end

-- 재실행이면 현재가·리더·마감 시각 같은 최신 상태는 보존하고, 구버전 Hash에 없던
-- 표시 메타데이터만 보충한다. 참여자 Set/Hash는 위에서 항상 DB 스냅샷으로 보충한다.
if itemType == 'hash' then
    redis.call('HSETNX', KEYS[1], 'itemName', ARGV[13])
    return 0
end

-- 물품 Hash를 마지막에 공개한다. 전체 스크립트가 원자적이라 다른 명령은 이 중간을 볼 수 없다.
redis.call('HSET', KEYS[1],
        'roomId', ARGV[1],
        'sellerUserId', ARGV[2],
        'status', ARGV[3],
        'startingPrice', ARGV[4],
        'currentPrice', ARGV[5],
        'bidIncrement', ARGV[6],
        'endAt', ARGV[7],
        'softCloseTriggerSeconds', ARGV[8],
        'softCloseExtendSeconds', ARGV[9],
        'totalExtensionSeconds', ARGV[10],
        'maxTotalExtensionSeconds', ARGV[11],
        'itemName', ARGV[13])

if ARGV[12] ~= '' then
    redis.call('HSET', KEYS[1], 'leaderUserId', ARGV[12])
end

return 1
