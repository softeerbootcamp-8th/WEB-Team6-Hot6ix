#!/usr/bin/env bash
# 측정용 데이터를 결정적으로 만든다. 같은 인자를 주면 항상 같은 모양이 나온다.
#
#   ./perf/seed.sh --users 200 --items 20 --start-price 10000 --unit 1000
#
# 만드는 것
#   1. 판매자 하나 (dev-login key=seller) + 판매자 프로필
#   2. 경매방 하나
#   3. 상품 N개 → 경매방 물품 N개 (벌크)
#   4. --start all 이면 전부 시작 (IN_PROGRESS)
#   5. 구매자 M명 (dev-login key=bidder-1..M) + 각자 이 방 약관 동의
#
# 왜 5번이 필요한가: 입찰은 auction_participants 에 agreed_at 이 채워진 행을 요구한다.
# 그게 없으면 부하를 아무리 걸어도 전부 TERMS_NOT_AGREED 로 거절돼서 아무것도 못 잰다.
#
# 마지막에 SHARE_CODE / ITEM_IDS / CLOSE_ITEM_IDS 를 --out 파일에 shell 변수로 남긴다.
# run.sh 가 그걸 읽어 k6 에 넘긴다.
#
# macOS 기본 bash(3.2)에서도 돌게 쓴다. mapfile 이나 연관 배열을 쓰지 않는 이유가 그것이다.

set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080/api/v1}"
USERS=200
ITEMS=20
START_PRICE=10000
UNIT=1000
START_MODE="all"
DURATION_MINUTES=720
OUT=""
# 동의 요청을 몇 개씩 동시에 보낼지. 200명을 한 줄로 세우면 시딩만 1분 넘게 걸린다.
CONCURRENCY=16

usage() {
  cat <<'USAGE'
사용법: seed.sh [옵션]

  --users N          입찰자 수 (기본 200)
  --items N          물품 수 (기본 20)
  --start-price N    시작가 (기본 10000)
  --unit N           입찰 단위 (기본 1000)
  --start all|none   물품을 시작할지 (기본 all). 시나리오 4는 none 으로 두고 k6 가 시작한다
  --duration-min N   시작할 때 줄 경매 시간(분). 기본 720 = 12시간, 측정 중에 안 닫히게
  --out FILE         결과를 shell 변수로 적을 파일
  --base URL         API 주소 (기본 http://localhost:8080/api/v1 = 개발 앱)
USAGE
}

while [ $# -gt 0 ]; do
  case "$1" in
    --users) USERS="$2"; shift 2 ;;
    --items) ITEMS="$2"; shift 2 ;;
    --start-price) START_PRICE="$2"; shift 2 ;;
    --unit) UNIT="$2"; shift 2 ;;
    --start) START_MODE="$2"; shift 2 ;;
    --duration-min) DURATION_MINUTES="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --base) BASE="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "모르는 옵션: $1" >&2; usage; exit 1 ;;
  esac
done

command -v jq >/dev/null || { echo "jq 가 필요하다: brew install jq" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

SELLER_JAR="$WORK/seller.cookie"

# 응답을 그대로 돌려주되, success 가 false 면 여기서 멈춘다.
# 조용히 넘어가면 시딩이 반쯤 된 채로 측정이 시작되고, 그 실행은 나중에 왜 이상한지 모른다.
api() {
  local jar="$1" method="$2" path="$3" body="${4:-}"
  local response

  if [ -n "$body" ]; then
    response="$(curl -sS -X "$method" "$BASE$path" \
      -b "$jar" -c "$jar" \
      -H 'Content-Type: application/json' \
      -d "$body")"
  else
    response="$(curl -sS -X "$method" "$BASE$path" -b "$jar" -c "$jar")"
  fi

  if [ "$(printf '%s' "$response" | jq -r '.success // false')" != "true" ]; then
    echo "요청 실패: $method $path" >&2
    printf '%s\n' "$response" >&2
    exit 1
  fi

  printf '%s' "$response"
}

echo "[seed] 판매자 로그인"
api "$SELLER_JAR" POST "/auth/dev-login?key=seller" >/dev/null

echo "[seed] 판매자 프로필"
# 이미 있으면 DUPLICATE_SELLER_PROFILE 이 온다. run.sh 는 DB 를 비우고 시작하므로 정상 경로에서는
# 안 나오지만, seed.sh 만 두 번 돌린 경우에는 그냥 넘어가야 한다.
curl -sS -o /dev/null -X POST "$BASE/seller-profiles" -b "$SELLER_JAR" -c "$SELLER_JAR" \
  -H 'Content-Type: application/json' \
  -d '{"storeName":"부하측정상점"}' || true

echo "[seed] 경매방 생성"
ROOM_JSON="$(api "$SELLER_JAR" POST "/auction-rooms" \
  "$(jq -nc --argjson unit "$UNIT" \
    '{name:"부하측정방", bidIncrement:$unit, softCloseTriggerSeconds:60, softCloseExtendSeconds:60}')")"

ROOM_ID="$(printf '%s' "$ROOM_JSON" | jq -r '.data.auctionRoomId')"
SHARE_CODE="$(printf '%s' "$ROOM_JSON" | jq -r '.data.shareCode')"
echo "[seed]   roomId=$ROOM_ID shareCode=$SHARE_CODE"

echo "[seed] 상품 $ITEMS 개"
PRODUCT_IDS=""
i=1
while [ "$i" -le "$ITEMS" ]; do
  PRODUCT_JSON="$(api "$SELLER_JAR" POST "/products" \
    "$(jq -nc --arg name "측정상품-$i" '{name:$name, description:"부하 측정용"}')")"
  PRODUCT_ID="$(printf '%s' "$PRODUCT_JSON" | jq -r '.data.productId')"
  PRODUCT_IDS="${PRODUCT_IDS:+$PRODUCT_IDS,}$PRODUCT_ID"
  i=$((i + 1))
done

echo "[seed] 경매방 물품 $ITEMS 개 (벌크)"
BULK_BODY="$(jq -nc \
  --arg ids "$PRODUCT_IDS" \
  --argjson price "$START_PRICE" \
  '{items: [$ids | split(",") | .[] | {productId: (. | tonumber), startingPrice: $price}]}')"

BULK_JSON="$(api "$SELLER_JAR" POST "/auction-rooms/$ROOM_ID/auction-items/bulk" "$BULK_BODY")"

if [ "$(printf '%s' "$BULK_JSON" | jq -r '.data.failed | length')" != "0" ]; then
  echo "[seed] 물품 추가가 일부 거절됐다:" >&2
  printf '%s' "$BULK_JSON" | jq -r '.data.failed' >&2
  exit 1
fi

ITEM_IDS_ALL="$(printf '%s' "$BULK_JSON" | jq -r '[.data.added[].auctionItemId] | join(",")')"

STARTED_IDS=""
READY_IDS=""

if [ "$START_MODE" = "all" ]; then
  echo "[seed] 물품 시작 (${DURATION_MINUTES}분)"
  START_BODY="$(jq -nc --argjson d "$DURATION_MINUTES" '{durationMinutes:$d}')"

  for item_id in $(printf '%s' "$ITEM_IDS_ALL" | tr ',' ' '); do
    api "$SELLER_JAR" POST "/auction-items/$item_id/start" "$START_BODY" >/dev/null
  done

  STARTED_IDS="$ITEM_IDS_ALL"
else
  echo "[seed] 물품은 READY 로 둔다 (k6 가 시작한다)"
  READY_IDS="$ITEM_IDS_ALL"
fi

echo "[seed] 구매자 $USERS 명 로그인 + 약관 동의 (동시 $CONCURRENCY)"
# 한 명당 쿠키 항아리 하나. 세션이 섞이면 한 사람이 여러 번 동의하고 나머지는 동의를 못 한다.
#
# -f 를 붙여 4xx·5xx 를 실패로 만든다. 없으면 로그인이나 동의가 거절돼도 조용히 넘어가고,
# 3분을 다 재고 나서야 "그 밖의 거절" 경고로 드러난다. 그 실행은 통째로 버려야 한다.
seed_one_buyer() {
  jar="$WORK/buyer-$1.cookie"

  curl -fsS -o /dev/null -X POST "$BASE/auth/dev-login?key=bidder-$1" -c "$jar" || return 1
  curl -fsS -o /dev/null -X POST "$BASE/auction-rooms/share/$SHARE_CODE/agreement" -b "$jar" || return 1
}
export -f seed_one_buyer
export BASE SHARE_CODE WORK

# 실패한 사람 수를 센다. xargs 는 자식이 실패하면 123 으로 끝나지만 몇 명인지는 안 알려준다.
FAIL_LOG="$WORK/failed"
: >"$FAIL_LOG"
seq 1 "$USERS" \
  | xargs -P "$CONCURRENCY" -I{} bash -c 'seed_one_buyer "$@" || echo "$1" >>"'"$FAIL_LOG"'"' _ {} {} \
  || true

FAILED="$(wc -l <"$FAIL_LOG" | tr -d ' ')"
if [ "$FAILED" != "0" ]; then
  echo "[seed] 구매자 ${FAILED}명이 로그인 또는 약관 동의에 실패했다." >&2
  echo "       이대로 재면 그 사람들의 입찰이 전부 거절돼 숫자가 의미 없어진다. 여기서 멈춘다." >&2
  exit 1
fi

echo "[seed] 완료"

emit() {
  echo "SHARE_CODE=$SHARE_CODE"
  echo "ROOM_ID=$ROOM_ID"
  echo "ITEM_IDS=$STARTED_IDS"
  echo "CLOSE_ITEM_IDS=$READY_IDS"
  echo "SEEDED_USERS=$USERS"
  echo "START_PRICE=$START_PRICE"
  echo "BID_UNIT=$UNIT"
}

if [ -n "$OUT" ]; then
  emit >"$OUT"
else
  emit
fi
