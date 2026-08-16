#!/bin/bash
# bid.lua 자체 검사. 진짜 Redis 에 올려서 반환값을 확인한다 (이슈 #246 의 비교군 C).
#
#   bash backend/perf/bid-lua-check.sh
#
# 단위 테스트로는 이 스크립트를 못 잡는다. Lua 는 Redis 안에서 돌고, 큰 수를 문자열로
# 바꿀 때 정밀도가 깨지는 것도 Redis 에 실제로 넣어 봐야 보인다.
set -u
LUA="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/lua/bid.lua"
IMAGE=redis:8.10.0-alpine
C=bid-lua-check

docker rm -f "$C" >/dev/null 2>&1
docker run -d --rm --name "$C" "$IMAGE" >/dev/null || { echo "FAIL 컨테이너 기동"; exit 1; }
for _ in $(seq 1 30); do
  docker exec "$C" redis-cli PING >/dev/null 2>&1 && break
  sleep 1
done
docker cp "$LUA" "$C":/bid.lua >/dev/null

cli() { docker exec -i "$C" redis-cli "$@"; }
# KEYS[1] 물품 HASH 하나뿐이다. 방 참여자 SET 은 스크립트가 HASH 의 roomId 로 만든다.
# ARGV 는 userId, 금액, 판정 시각.
run() { docker exec -i "$C" redis-cli --eval /bid.lua "$1" , "$2" "$3" "$4"; }

FAIL=0
check() { # 이름 기대값 실제값
  if [ "$2" = "$3" ]; then printf 'ok   %-32s %s\n' "$1" "$3"
  else printf 'FAIL %-32s 기대 %s 실제 %s\n' "$1" "$2" "$3"; FAIL=1; fi
}

NOW=1000000
FUTURE=2000000
PAST=500000
ROOM=1
# 스크립트가 이 이름을 roomId 로 만든다. 여기서 바꾸면 스크립트도 같이 바꿔야 한다.
P=auction:room:$ROOM:participants

# 참여자 7·8·10 만 동의했다. 9 는 동의하지 않은 사용자, 99 는 판매자다.
cli SADD "$P" 7 8 10 >/dev/null

# 시작가 10000, 단위 1000, 아직 입찰 없음(leaderUserId 필드 없음)
cli HSET item:1 endAt $FUTURE startingPrice 10000 bidIncrement 1000 \
  currentPrice 10000 sellerUserId 99 roomId $ROOM >/dev/null
cli HSET item:2 endAt $PAST startingPrice 10000 bidIncrement 1000 \
  currentPrice 10000 sellerUserId 99 roomId $ROOM >/dev/null

check "키 없음 → -1"              "-1" "$(run item:none 7 10000 $NOW)"
check "판매자 → 5"                "5"  "$(run item:1 99 10000 $NOW)"
check "미동의자 → 6"              "6"  "$(run item:1 9 10000 $NOW)"
check "마감 지남 → 1"             "1"  "$(run item:2 7 10000 $NOW)"
check "시작가 미만 → 3"           "3"  "$(run item:1 7 9000 $NOW)"
check "단위 안 맞음 → 4"          "4"  "$(run item:1 7 10500 $NOW)"
check "첫 입찰 시작가와 같음 → 0" "0"  "$(run item:1 7 10000 $NOW)"
check "  currentPrice"            "10000" "$(cli HGET item:1 currentPrice)"
check "  leaderUserId"            "7"     "$(cli HGET item:1 leaderUserId)"

check "이미 최고입찰자 → 2"       "2"  "$(run item:1 7 11000 $NOW)"
check "현재가+단위 미만 → 3"      "3"  "$(run item:1 8 10000 $NOW)"
check "현재가+단위 → 0"           "0"  "$(run item:1 8 11000 $NOW)"
check "  currentPrice"            "11000" "$(cli HGET item:1 currentPrice)"
check "  leaderUserId"            "8"     "$(cli HGET item:1 leaderUserId)"

# 판매자 검사가 참여자 검사보다 앞이어야 한다. 판매자는 방에 약관 동의를 하지 않아
# 참여자 SET 에 없으므로, 순서가 반대면 판매자가 6 을 받는다.
check "판매자는 6 아니라 5"       "5"  "$(run item:1 99 12000 $NOW)"

# 마감 판정이 자격 검사보다 뒤여야 Java 와 거절 사유 분포가 같다.
check "마감된 물품의 미동의자 → 6" "6" "$(run item:2 9 10000 $NOW)"

# Lua 5.1 의 tostring 은 큰 수를 1e+15 로 바꾼다. string.format('%d') 를 쓰는지 본다.
cli HSET item:3 endAt $FUTURE startingPrice 1000000000000000 bidIncrement 1000 \
  currentPrice 1000000000000000 sellerUserId 99 roomId $ROOM >/dev/null
check "1000조 접수 → 0"           "0" "$(run item:3 10 1000000000000000 $NOW)"
check "  큰 수가 안 뭉개짐"       "1000000000000000" "$(cli HGET item:3 currentPrice)"
check "  그 다음 입찰 판정"       "3" "$(run item:3 7 1000000000000000 $NOW)"

docker rm -f "$C" >/dev/null 2>&1
echo
[ "$FAIL" = 0 ] && echo "전부 통과" || echo "실패 있음"
exit "$FAIL"
