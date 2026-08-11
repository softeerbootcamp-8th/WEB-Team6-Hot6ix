-- 입찰 판정과 현재가 갱신을 한 번에 한다
--
-- BidService 의 validateEntitled·validateBiddable·validateAmount 가 하던 판정을 전부 옮긴 것이다.
-- 거절된 요청은 여기서 끝나고 MySQL 커넥션을 잡지 않도록 하여 저희의 목표인 DB에 CPU 부하를 줄이고자 했습니다.
-- 기존 부하 측정에서 요청의 94% 가 거절이었고 그게 전부 트랜잭션 안에 포함되어버리는 바람에 MySQL CPU 자원을 소모하고 있었습니다.
--
-- 셋 중 validateEntitled까지 옮긴 이유는 이걸 DB에 남기면 거절된 요청도 커넥션을 잡고 트랜잭션을 열어 SELECT 를 세 번 한 뒤 Redis 에서 거절됩니다.
-- 그렇게 되면 사전에 목표했던 Redis를 도입함으로써 DB CPU 부하를 줄이는 목표를 이행하기 어려울 것이라 판단하여 옮겼습니다.
--
-- KEYS[1] 물품 HASH 조회
--          endAt(epoch millis), currentPrice, leaderUserId, startingPrice, bidIncrement,
--          sellerUserId
--          status 는 안 넣는다. endAt 하나로 READY·마감·조기마감이 전부 걸린다.
--          sellerUserId 는 방 값이지만 물품마다 복사해 둔다. 판매자는 안 바뀌므로 복사가
--          어긋나지 않고, 여기서 키를 하나 덜 읽는다. bidIncrement 도 방에서 복사한 값이다.
-- KEYS[2] 방 참여자 SET. 약관 동의를 마친 userId 가 들어 있다.
--          물품 HASH 와 같이 만들어진다 — 키 없음 복구가 둘을 한 번에 채운다.
--          그래서 SISMEMBER 가 0 이면 "SET 이 날아갔다" 가 아니라 "참여자가 아니다" 다.
--          maxmemory-policy 를 noeviction 으로 둬야 이 성질이 유지된다.
-- ARGV[1] 입찰자 userId
-- ARGV[2] 입찰 금액
-- ARGV[3] 판정 기준 시각 (epoch millis). Java 가 Clock 으로 만든 값을 넘긴다 —
--         redis.call('TIME') 을 쓰면 스크립트가 비결정적이 되고 앱 시계와도 갈린다.
--
-- 반환값. 거절 사유를 그대로 BidErrorType 으로 옮기려고 정수로 돌려준다.
--    0  접수. currentPrice 와 leaderUserId 를 이 스크립트가 갱신했다
--   -1  키 없음. 부르는 쪽이 DB 에서 읽어 채우고 다시 부른다
--    1  ITEM_CLOSED
--    2  ALREADY_TOP_BIDDER
--    3  BID_AMOUNT_TOO_LOW
--    4  INVALID_BID_UNIT
--    5  SELLER_CANNOT_BID
--    6  TERMS_NOT_AGREED
--
-- 검사 순서는 Java 와 같게 둔다. 거절 사유 분포를 A 와 나란히 놓고 봐야 하기 때문이다.
-- 판매자 검사가 참여자 검사보다 앞인 것도 Java 와 같다 — 판매자는 자기 방에 약관 동의를
-- 하지 않아 참여 행이 없어서, 순서가 반대면 판매자가 TERMS_NOT_AGREED 를 받는다.

local endAt = redis.call('HGET', KEYS[1], 'endAt')

-- 키가 없으면 HGET 이 false 를 준다. 여기서 거절하면 Redis 를 한 번 재시작하는 것으로
-- 진행 중인 경매가 전부 멈춘다. perf 구성은 --appendonly no 라 재시작하면 반드시 비어 있다.
if endAt == false then
    return -1
end

local bidderId = ARGV[1]

if redis.call('HGET', KEYS[1], 'sellerUserId') == bidderId then
    return 5
end

local roomId = redis.call('HGET', KEYS[1], 'roomId')

if redis.call('SISMEMBER', 'auction:room:' .. roomId .. ':participants', bidderId) == 0 then
    return 6
end

-- validateBiddable 의 마감 판정과 같다. arrivedAt 이 endAt 보다 앞서야만 받는다.
if tonumber(ARGV[3]) >= tonumber(endAt) then
    return 1
end

local leaderUserId = redis.call('HGET', KEYS[1], 'leaderUserId')

if leaderUserId == bidderId then
    return 2
end

local amount = tonumber(ARGV[2])
local startingPrice = tonumber(redis.call('HGET', KEYS[1], 'startingPrice'))
local bidIncrement = tonumber(redis.call('HGET', KEYS[1], 'bidIncrement'))

-- validateAmount 와 같은 식이다. 입찰이 아직 없으면 시작가, 있으면 현재가 + 단위.
-- 시작가와 같은 금액으로 첫 입찰을 할 수 있어야 해서 "현재가 초과" 가 아니다.
local minimum
if leaderUserId == false then
    minimum = startingPrice
else
    minimum = tonumber(redis.call('HGET', KEYS[1], 'currentPrice')) + bidIncrement
end

if amount < minimum then
    return 3
end

if (amount - startingPrice) % bidIncrement ~= 0 then
    return 4
end

-- 문자열로 만들어 넣는다. Lua 5.1 의 tostring 은 큰 수를 1e+15 꼴로 바꿔서, 그대로 쓰면
-- 다음 요청의 tonumber 가 정밀도를 잃은 값을 읽는다.
redis.call('HSET', KEYS[1],
        'currentPrice', string.format('%d', amount),
        'leaderUserId', bidderId)

return 0
