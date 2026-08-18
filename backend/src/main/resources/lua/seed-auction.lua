-- KEYS[1] 물품 Hash
-- KEYS[2] 방 참여자 Set
-- KEYS[3] 방 참여자 nickname Hash
-- KEYS[4] 물품 리더보드 ZSET
--
-- ARGV[1..13] 물품의 고정 필드 값(itemName 포함)
-- ARGV[14]     약관에 동의한 참여자 수
-- ARGV[15..]   참여자의 userId, nickname 쌍, 리더보드 수, bidderUserId, amount 쌍
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

local leaderboardType = keyType(KEYS[4])
if leaderboardType ~= 'none' and leaderboardType ~= 'zset' then
    error('auction leaderboard key has invalid type: ' .. leaderboardType)
end

if ARGV[13] == nil or ARGV[13] == '' then
    error('auction item itemName is missing')
end

local participantCount = tonumber(ARGV[14])
if participantCount == nil or participantCount < 0 or participantCount % 1 ~= 0 then
    error('auction participant count must be a non-negative integer')
end
local leaderboardCountIndex = 15 + participantCount * 2
local leaderboardCount = tonumber(ARGV[leaderboardCountIndex])
if leaderboardCount == nil or leaderboardCount < 0 or leaderboardCount % 1 ~= 0 then
    error('auction leaderboard count must be a non-negative integer')
end
if #ARGV ~= leaderboardCountIndex + leaderboardCount * 2 then
    error('auction seed metadata count does not match arguments')
end

for index = 15, leaderboardCountIndex - 1, 2 do
    if ARGV[index] == '' or string.match(ARGV[index], '^%d+$') == nil then
        error('auction participant userId is invalid')
    end
    if ARGV[index + 1] == '' then
        error('auction participant nickname is missing')
    end
end
for index = leaderboardCountIndex + 1, #ARGV, 2 do
    if ARGV[index] == '' or string.match(ARGV[index], '^%d+$') == nil then
        error('auction leaderboard bidderUserId is invalid')
    end
    if ARGV[index + 1] == '' or string.match(ARGV[index + 1], '^%d+$') == nil then
        error('auction leaderboard amount is invalid')
    end
end

local leaderboardMatches = true
local currentLeader = redis.call('HGET', KEYS[1], 'leaderUserId')
if currentLeader ~= false and currentLeader ~= '' then
    local currentPrice = redis.call('HGET', KEYS[1], 'currentPrice')
    local leaderAmount = leaderboardType == 'zset'
            and redis.call('ZSCORE', KEYS[4], currentLeader) or false
    leaderboardMatches = currentPrice ~= false
            and leaderAmount ~= false
            and tonumber(currentPrice) ~= nil
            and tonumber(leaderAmount) == tonumber(currentPrice)
end

-- 세 키와 완료 표시가 모두 준비된 정상 Seed에는 DB 스냅샷을 다시 적용하지 않는다.
--
-- itemName 을 조건에 함께 두는 것은, 아래 HSETNX 보충이 이 조기 반환에 막히면 안 되기
-- 때문이다. seed-ready.lua 의 판정 조건과 같아야 한다 (#374).
if itemType == 'hash'
        and participantsType == 'set'
        and nicknamesType == 'hash'
        and redis.call('HEXISTS', KEYS[1], 'itemName') == 1
        and redis.call('HEXISTS', KEYS[1], 'revision') == 1
        and redis.call('HGET', KEYS[1], 'leaderboardSeeded') == '1'
        and leaderboardMatches
        and redis.call('SISMEMBER', KEYS[2], participantsReadyMarker) == 1
        and redis.call('HEXISTS', KEYS[3], nicknamesReadyMarkerField) == 1 then
    return 0
end

-- 입찰 Lua가 물품 Hash를 발견했을 때 참여자 ID와 nickname이 모두 준비돼 있도록 먼저 쓴다.
redis.call('HSET', KEYS[3], nicknamesReadyMarkerField, '1')
for index = 15, leaderboardCountIndex - 1, 2 do
    redis.call('HSET', KEYS[3], ARGV[index], ARGV[index + 1])
    redis.call('SADD', KEYS[2], ARGV[index])
end
redis.call('SADD', KEYS[2], participantsReadyMarker)

for index = leaderboardCountIndex + 1, #ARGV, 2 do
    redis.call('ZADD', KEYS[4], ARGV[index + 1], ARGV[index])
end

-- 재실행이면 현재가·리더·마감 시각 같은 최신 상태는 보존하고, 구버전 Hash에 없던
-- 표시 메타데이터만 보충한다. 참여자 Set/Hash는 위에서 항상 DB 스냅샷으로 보충한다.
if itemType == 'hash' then
    redis.call('HSETNX', KEYS[1], 'itemName', ARGV[13])
    redis.call('HSETNX', KEYS[1], 'revision', '0')
    local currentLeader = redis.call('HGET', KEYS[1], 'leaderUserId')
    local currentPrice = redis.call('HGET', KEYS[1], 'currentPrice')
    if currentLeader ~= false and currentLeader ~= ''
            and currentPrice ~= false and currentPrice ~= '' then
        redis.call('ZADD', KEYS[4], currentPrice, currentLeader)
    end
    redis.call('HSET', KEYS[1], 'leaderboardSeeded', '1')
    return 0
end

-- 물품 Hash를 마지막에 공개한다. 전체 스크립트가 원자적이라 다른 명령은 이 중간을 볼 수 없다.
-- DB 상위 조회는 탈퇴 회원을 제외하므로, 엔티티에 남은 현재 선두는 별도로 보존한다.
if ARGV[12] ~= '' then
    redis.call('ZADD', KEYS[4], ARGV[5], ARGV[12])
end

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
        'itemName', ARGV[13],
        'revision', '0',
        'leaderboardSeeded', '1')

if ARGV[12] ~= '' then
    redis.call('HSET', KEYS[1], 'leaderUserId', ARGV[12])
end

return 1
