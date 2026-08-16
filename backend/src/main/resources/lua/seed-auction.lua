-- KEYS[1] 물품 Hash
-- KEYS[2] 방 참여자 Set
--
-- ARGV[1..12] 물품의 고정 필드 값
-- ARGV[13..]  약관에 동의한 참여자 userId
--
-- 입찰 Lua가 물품 Hash를 발견했을 때 참여자 Set도 준비돼 있도록 Set을 먼저 쓴다.
-- 재실행 때 Hash가 이미 있어도 누락된 참여자 Set은 DB 스냅샷으로 다시 채운다.
for index = 13, #ARGV do
    redis.call('SADD', KEYS[2], ARGV[index])
end

-- 물품 Hash가 이미 있으면 진행 중 Redis 상태를 DB 스냅샷으로 되돌리지 않는다.
if redis.call('EXISTS', KEYS[1]) == 1 then
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
