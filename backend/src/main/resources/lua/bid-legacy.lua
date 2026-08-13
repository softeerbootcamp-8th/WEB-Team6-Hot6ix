-- Task 8에서 현재 동기 BidService를 새 Redis-first API로 전환할 때 제거할 비교군 C 스크립트다.
local endAt = redis.call('HGET', KEYS[1], 'endAt')
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

redis.call('HSET', KEYS[1],
        'currentPrice', string.format('%d', amount),
        'leaderUserId', bidderId)
return 0
