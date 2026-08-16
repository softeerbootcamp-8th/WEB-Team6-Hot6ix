-- KEYS[1] 물품 Hash
-- KEYS[2] 방 참여자 Set
--
-- ARGV[1..12] 물품의 고정 필드 값
-- ARGV[13..]  약관에 동의한 참여자 userId
--
-- 참여자가 없어도 Set이 준비됐음을 나타내는 내부 값이다. 실제 회원 ID는 숫자 문자열이라
-- bid.lua의 SISMEMBER 판정과 겹치지 않는다.
local participantsReadyMarker = '__seed_initialized__'

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

-- 정상 Seed에는 DB 스냅샷을 다시 적용하지 않는다.
if itemType == 'hash' and participantsType == 'set' then
    return 0
end

-- 입찰 Lua가 물품 Hash를 발견했을 때 참여자 Set도 준비돼 있도록 Set을 먼저 쓴다.
-- Hash만 남고 Set이 유실됐으면 참여자만 복구하며 최신 입찰 상태는 보존한다.
for index = 13, #ARGV do
    redis.call('SADD', KEYS[2], ARGV[index])
end
redis.call('SADD', KEYS[2], participantsReadyMarker)

-- 물품 Hash가 이미 있으면 진행 중 Redis 상태를 DB 스냅샷으로 되돌리지 않는다.
if itemType == 'hash' then
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
        'maxTotalExtensionSeconds', ARGV[11])

if ARGV[12] ~= '' then
    redis.call('HSET', KEYS[1], 'leaderUserId', ARGV[12])
end

return 1
