-- KEYS[1] 물품 Hash
-- KEYS[2] 방 참여자 Set
-- KEYS[3] 방 참여자 nickname Hash
--
-- 복구 전에 세 Seed 키의 타입과 완료 표시만 확인한다. 값 전체를 읽지 않으므로 정상 상태에서는
-- DB 참여자 스냅샷 조회와 seed-auction.lua 실행을 건너뛸 수 있다.
--
-- itemName 은 완료 표시와 함께 본다. #365 이전에 만들어진 물품 Hash 에는 이 필드가 없는데
-- close-auction.lua 와 bid.lua 가 필수로 요구해서, 없으면 그 물품은 입찰도 마감도 실패한다.
-- 보충은 seed-auction.lua 의 HSETNX 가 하지만 여기서 준비됨으로 판정해 버리면 Seed 복구가
-- 조기 반환해서 그 보충이 영영 돌지 않는다 (#374).
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
local nicknamesType = keyType(KEYS[3])

if itemType ~= 'none' and itemType ~= 'hash' then
    error('auction item key has invalid type: ' .. itemType)
end
if participantsType ~= 'none' and participantsType ~= 'set' then
    error('auction participants key has invalid type: ' .. participantsType)
end
if nicknamesType ~= 'none' and nicknamesType ~= 'hash' then
    error('auction participant nicknames key has invalid type: ' .. nicknamesType)
end

if itemType == 'hash'
        and participantsType == 'set'
        and nicknamesType == 'hash'
        and redis.call('HEXISTS', KEYS[1], 'itemName') == 1
        and redis.call('SISMEMBER', KEYS[2], participantsReadyMarker) == 1
        and redis.call('HEXISTS', KEYS[3], nicknamesReadyMarkerField) == 1 then
    return 1
end
return 0
