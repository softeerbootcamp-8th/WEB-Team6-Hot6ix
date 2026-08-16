-- KEYS[1] 물품 Hash
-- KEYS[2] 방 참여자 Set
--
-- 복구 전에 두 Seed 키의 타입만 확인한다. 값 전체를 읽지 않으므로 정상 상태에서는
-- DB 참여자 스냅샷 조회와 seed-auction.lua 실행을 건너뛸 수 있다.
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

if itemType == 'hash' and participantsType == 'set' then
    return 1
end
return 0
