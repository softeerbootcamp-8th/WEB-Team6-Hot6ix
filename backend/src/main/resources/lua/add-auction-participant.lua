-- 약관 동의를 마친 참여자의 판정용 ID와 표시용 nickname을 원자적으로 준비한다.
-- KEYS[1] 방 참여자 Set
-- KEYS[2] 방 참여자 nickname Hash
-- ARGV[1] userId
-- ARGV[2] nickname

local function keyType(key)
    local response = redis.call('TYPE', key)
    if type(response) == 'table' then
        return response['ok']
    end
    return response
end

local participantsType = keyType(KEYS[1])
if participantsType ~= 'none' and participantsType ~= 'set' then
    error('auction participants key has invalid type: ' .. participantsType)
end

local nicknamesType = keyType(KEYS[2])
if nicknamesType ~= 'none' and nicknamesType ~= 'hash' then
    error('auction participant nicknames key has invalid type: ' .. nicknamesType)
end

if ARGV[1] == nil or ARGV[1] == '' or ARGV[2] == nil or ARGV[2] == '' then
    error('auction participant metadata is incomplete')
end

redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
redis.call('SADD', KEYS[1], ARGV[1])
return 1
