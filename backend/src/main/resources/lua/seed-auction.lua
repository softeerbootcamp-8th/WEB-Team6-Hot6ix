-- KEYS[1] 물품 Hash
-- KEYS[2] 방 참여자 Set
-- KEYS[3] 방 참여자 nickname Hash
--
-- ARGV[1..13] 물품의 고정 필드 값(itemName 포함)
-- ARGV[14..]   약관에 동의한 참여자의 userId, nickname 쌍
--
-- 참여자가 없어도 Set이 준비됐음을 나타내는 내부 값이다. 실제 회원 ID는 숫자 문자열이라
-- bid.lua의 SISMEMBER 판정과 겹치지 않는다.
local participantsReadyMarker = '__seed_initialized__'
local nicknamesReadyMarkerField = '__seed_initialized__'

local function keyType(key)
    local response = redis.call('TYPE', key)
    if type(response) == 'table' then
        return response['ok']
    end
    return response
end

local itemType = keyType(KEYS[1])
local participantsType = keyType(KEYS[2])

if itemType ~= 'none' and itemType ~= 'hash' then
    error('auction item key has invalid type: ' .. itemType)
end

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

-- 세 키와 완료 표시가 모두 준비된 정상 Seed에는 DB 스냅샷을 다시 적용하지 않는다.
--
-- itemName 을 조건에 함께 두는 것은, 아래 HSETNX 보충이 이 조기 반환에 막히면 안 되기
-- 때문이다. seed-ready.lua 의 판정 조건과 같아야 한다 (#374).
if itemType == 'hash'
        and participantsType == 'set'
        and nicknamesType == 'hash'
        and redis.call('HEXISTS', KEYS[1], 'itemName') == 1
        and redis.call('SISMEMBER', KEYS[2], participantsReadyMarker) == 1
        and redis.call('HEXISTS', KEYS[3], nicknamesReadyMarkerField) == 1 then
    return 0
end

-- 입찰 Lua가 물품 Hash를 발견했을 때 참여자 ID와 nickname이 모두 준비돼 있도록 먼저 쓴다.
redis.call('HSET', KEYS[3], nicknamesReadyMarkerField, '1')
for index = 14, #ARGV, 2 do
    if ARGV[index + 1] == '' then
        error('auction participant nickname is missing')
    end
    redis.call('HSET', KEYS[3], ARGV[index], ARGV[index + 1])
    redis.call('SADD', KEYS[2], ARGV[index])
end
redis.call('SADD', KEYS[2], participantsReadyMarker)

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
