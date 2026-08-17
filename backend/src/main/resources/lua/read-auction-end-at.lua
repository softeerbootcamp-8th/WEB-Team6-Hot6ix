-- KEYS[1..n] 물품 Hash
--
-- 진행 중 물품의 마감 시각을 한 번에 읽는다. 마감 예약 재동기화가 큐를 무엇에 맞출지 정하는
-- 데 쓴다. Hash 가 없거나 endAt 이 비면 빈 문자열이라, 부르는 쪽이 DB 값으로 넘어간다 (#374).
--
-- 한 번에 몰아 읽는 것은 진행 중 물품 수만큼 왕복하지 않기 위해서다. 스크립트가 도는 동안
-- Redis 가 다른 명령을 못 받으므로 부르는 쪽이 개수를 잘라서 넘긴다.

local result = {}
for index = 1, #KEYS do
    local value = redis.call('HGET', KEYS[index], 'endAt')
    if value == false then
        result[index] = ''
    else
        result[index] = value
    end
end
return result
