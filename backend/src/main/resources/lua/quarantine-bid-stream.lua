-- 실패한 원본 Stream 이벤트를 중복 없이 격리한다.
-- KEYS[1] quarantine Stream
-- KEYS[2] originalRecordId -> quarantineRecordId Index Hash

local function keyType(key)
    local response = redis.call('TYPE', key)
    if type(response) == 'table' then
        return response['ok']
    end
    return response
end

local function requireKeyType(key, expected, label)
    local actual = keyType(key)
    if actual ~= expected and actual ~= 'none' then
        error(label .. ' key has invalid type: ' .. actual)
    end
end

requireKeyType(KEYS[1], 'stream', 'quarantine stream')
requireKeyType(KEYS[2], 'hash', 'quarantine index')

local existing = redis.call('HGET', KEYS[2], ARGV[2])
if existing ~= false then
    return {existing, '0'}
end

local quarantineRecordId = redis.call('XADD', KEYS[1], '*',
        'originalStreamKey', ARGV[1],
        'originalRecordId', ARGV[2],
        'eventType', ARGV[3],
        'rawFields', ARGV[4],
        'failureKind', ARGV[5],
        'failureCode', ARGV[6],
        'errorClass', ARGV[7],
        'errorMessage', ARGV[8],
        'deliveryCount', ARGV[9],
        'quarantinedAt', ARGV[10],
        'consumerName', ARGV[11])

redis.call('HSET', KEYS[2], ARGV[2], quarantineRecordId)
return {quarantineRecordId, '1'}
