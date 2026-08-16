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
# --close-room 을 주면 한 단계를 더 한다.
#   6. 물품마다 구매자 1..N(--bids-per-item)이 순서대로 입찰 (증가하는 금액)
#   7. 판매자 세션으로 방 종료 API 호출 — 진행 중 물품을 전부 닫고 방을 CLOSED 로 만든다
#
# 결과 조회(#329) 부하 시나리오가 쓴다. 낙찰자·낙찰 후보가 실제 도메인 경로로 생겨야
# GET .../results 가 SOLD 물품과 myRank 를 채운 상태로 응답한다.
#
# 마지막에 SHARE_CODE / ITEM_IDS / CLOSE_ITEM_IDS 를 --out 파일에 shell 변수로 남긴다.
# run.sh 가 그걸 읽어 k6 에 넘긴다.
#
# macOS 기본 bash(3.2)에서도 돌게 쓴다. mapfile 이나 연관 배열을 쓰지 않는 이유가 그것이다.

set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080/api/v1}"
USERS=200
ITEMS=20
# 방 하나에 넣을 물품 수. 0 이면 전부 한 방에 넣는다 (예전 동작).
# 서비스 규칙이 방당 동시 진행 3개라, 그 안에서 재려면 3 을 준다.
PER_ROOM=0
START_PRICE=10000
UNIT=1000
START_MODE="all"
DURATION_MINUTES=720
OUT=""
# 동의 요청을 몇 개씩 동시에 보낼지. 200명을 한 줄로 세우면 시딩만 1분 넘게 걸린다.
CONCURRENCY=16

# 마감 임박 구간. 물품 길이의 하한이 1분인데 트리거도 60초면 물품이 태어나자마자 임박 구간이라
# 입찰이 들어올 때마다 마감이 밀려서 영영 안 닫힌다. 그래서 시나리오 5 는 이 값을 낮춰 쓴다.
SOFT_CLOSE_TRIGGER=60
SOFT_CLOSE_EXTEND=60

# 결과 조회 부하 시나리오(#329) 전용. 기본은 꺼져 있어 기존 호출은 그대로 동작한다.
CLOSE_ROOM=0
BIDS_PER_ITEM=3

usage() {
  cat <<'USAGE'
사용법: seed.sh [옵션]

  --users N          입찰자 수 (기본 200)
  --items N          물품 수 (기본 20)
  --start-price N    시작가 (기본 10000)
  --unit N           입찰 단위 (기본 1000)
  --start all|none   물품을 시작할지 (기본 all). 시나리오 4는 none 으로 두고 k6 가 시작한다
  --duration-min N   시작할 때 줄 경매 시간(분). 기본 720 = 12시간, 측정 중에 안 닫히게
  --soft-close-trigger N  마감 임박으로 보는 남은 초 (기본 60)
  --soft-close-extend N   임박 구간에 입찰이 들어오면 밀어 줄 초 (기본 60)
  --close-room       물품마다 입찰을 넣고 방을 종료한다 (결과 조회 시나리오용, 기본 꺼짐)
  --bids-per-item N  --close-room 일 때 물품 하나에 넣을 입찰 수 (기본 3)
  --out FILE         결과를 shell 변수로 적을 파일
  --base URL         API 주소 (기본 http://localhost:8080/api/v1 = 개발 앱)
USAGE
}

while [ $# -gt 0 ]; do
  case "$1" in
    --users) USERS="$2"; shift 2 ;;
    --items) ITEMS="$2"; shift 2 ;;
    --per-room) PER_ROOM="$2"; shift 2 ;;
    --start-price) START_PRICE="$2"; shift 2 ;;
    --unit) UNIT="$2"; shift 2 ;;
    --start) START_MODE="$2"; shift 2 ;;
    --duration-min) DURATION_MINUTES="$2"; shift 2 ;;
    --soft-close-trigger) SOFT_CLOSE_TRIGGER="$2"; shift 2 ;;
    --soft-close-extend) SOFT_CLOSE_EXTEND="$2"; shift 2 ;;
    --close-room) CLOSE_ROOM=1; shift ;;
    --bids-per-item) BIDS_PER_ITEM="$2"; shift 2 ;;
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
  local -a auth=()

  # 배포에서는 dev-login 이 토큰을 아는 요청만 받는다 (#266). 로컬 perf 는 값이 없어서
  # 헤더가 안 붙고 지금까지와 똑같이 돈다. 다른 엔드포인트에 붙어도 무시된다.
  #
  # ${auth[@]+"${auth[@]}"} 는 set -u 에서 빈 배열을 펼쳐도 안 죽는 형태다 (bash 3.2).
  if [ -n "${DEV_LOGIN_TOKEN:-}" ]; then
    auth=(-H "X-Dev-Login-Token: $DEV_LOGIN_TOKEN")
  fi

  if [ -n "$body" ]; then
    response="$(curl -sS -X "$method" "$BASE$path" \
      -b "$jar" -c "$jar" \
      ${auth[@]+"${auth[@]}"} \
      -H 'Content-Type: application/json' \
      -d "$body")"
  else
    response="$(curl -sS -X "$method" "$BASE$path" -b "$jar" -c "$jar" ${auth[@]+"${auth[@]}"})"
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

# 방을 몇 개 만들지 정한다.
#
# --per-room 0 이면 예전처럼 한 방에 다 넣는다. 값을 주면 그 수만큼씩 나눠 담아서
# 서비스 규칙(방당 동시 진행 3개) 안에서도 물품을 여러 개 진행할 수 있게 한다.
# 제한을 넘겨 넣으면 4008 로 거절되는데 표에는 아무 표시도 안 남아서 알아채기 어렵다.
if [ "$PER_ROOM" -gt 0 ] 2>/dev/null; then
  ROOM_COUNT=$(( (ITEMS + PER_ROOM - 1) / PER_ROOM ))
else
  ROOM_COUNT=1
fi

echo "[seed] 경매방 $ROOM_COUNT 개 생성"
declare -a ROOM_IDS=()
declare -a ROOM_CODES=()

r=1
while [ "$r" -le "$ROOM_COUNT" ]; do
  ROOM_JSON="$(api "$SELLER_JAR" POST "/auction-rooms" \
    "$(jq -nc --argjson unit "$UNIT" \
         --arg name "부하측정방-$r" \
         --argjson trigger "$SOFT_CLOSE_TRIGGER" --argjson extend "$SOFT_CLOSE_EXTEND" \
      '{name:$name, bidIncrement:$unit,
        softCloseTriggerSeconds:$trigger, softCloseExtendSeconds:$extend}')")"

  ROOM_IDS+=("$(printf '%s' "$ROOM_JSON" | jq -r '.data.auctionRoomId')")
  ROOM_CODES+=("$(printf '%s' "$ROOM_JSON" | jq -r '.data.shareCode')")
  r=$((r + 1))
done

# 뒤쪽 코드와 emit 이 쓰는 대표값. 방이 하나면 예전과 완전히 같다.
ROOM_ID="${ROOM_IDS[0]}"
SHARE_CODE="${ROOM_CODES[0]}"
echo "[seed]   roomId=$ROOM_ID shareCode=$SHARE_CODE"
[ "$ROOM_COUNT" -gt 1 ] && echo "[seed]   방 $ROOM_COUNT 개, 방당 물품 $PER_ROOM 개"

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

# 벌크 API 는 한 번에 100개까지만 받는다(2002 로 거절된다). 그래서 100개씩 끊어 보낸다.
BULK_CHUNK=100

# 방 하나가 가져갈 상품 수. 방이 하나면 전부 그 방으로 간다.
if [ "$ROOM_COUNT" -gt 1 ]; then
  SLICE="$PER_ROOM"
else
  SLICE="$ITEMS"
fi

echo "[seed] 경매방 물품 $ITEMS 개 (벌크 ${BULK_CHUNK}개씩)"
ITEM_IDS_ALL=""
# 방마다 어떤 물품이 들어갔는지. 방 사이는 ; 로, 방 안은 , 로 나눈다.
# k6 가 이걸 읽어 VU 를 방에 배정한다.
ROOM_ITEM_GROUPS=""

r=0
while [ "$r" -lt "$ROOM_COUNT" ]; do
  ROOM_START=$((r * SLICE))
  ROOM_END=$((ROOM_START + SLICE))
  [ "$ROOM_END" -gt "$ITEMS" ] && ROOM_END="$ITEMS"

  GROUP_IDS=""
  OFFSET="$ROOM_START"

  while [ "$OFFSET" -lt "$ROOM_END" ]; do
    TAKE=$((ROOM_END - OFFSET))
    [ "$TAKE" -gt "$BULK_CHUNK" ] && TAKE="$BULK_CHUNK"

    BULK_BODY="$(jq -nc \
      --arg ids "$PRODUCT_IDS" \
      --argjson price "$START_PRICE" \
      --argjson from "$OFFSET" --argjson size "$TAKE" \
      '{items: [$ids | split(",") | .[$from:($from + $size)] | .[]
                | {productId: (. | tonumber), startingPrice: $price}]}')"

    BULK_JSON="$(api "$SELLER_JAR" POST "/auction-rooms/${ROOM_IDS[$r]}/auction-items/bulk" "$BULK_BODY")"

    if [ "$(printf '%s' "$BULK_JSON" | jq -r '.data.failed | length')" != "0" ]; then
      echo "[seed] 물품 추가가 일부 거절됐다:" >&2
      printf '%s' "$BULK_JSON" | jq -r '.data.failed' >&2
      exit 1
    fi

    CHUNK_IDS="$(printf '%s' "$BULK_JSON" | jq -r '[.data.added[].auctionItemId] | join(",")')"
    GROUP_IDS="${GROUP_IDS:+$GROUP_IDS,}$CHUNK_IDS"
    OFFSET=$((OFFSET + TAKE))
  done

  ITEM_IDS_ALL="${ITEM_IDS_ALL:+$ITEM_IDS_ALL,}$GROUP_IDS"
  ROOM_ITEM_GROUPS="${ROOM_ITEM_GROUPS:+$ROOM_ITEM_GROUPS;}$GROUP_IDS"
  r=$((r + 1))
done

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
#
# 두 번째 인자가 그 사람이 동의할 방이다. 방이 여러 개면 구매자를 방마다 나눠 붙인다.
# 전원이 모든 방에 동의하게 하면 요청이 사람 수 x 방 수로 늘어 시딩만 몇 배가 된다.
seed_one_buyer() {
  jar="$WORK/buyer-$1.cookie"
  code="$2"

  local -a auth=()
  if [ -n "${DEV_LOGIN_TOKEN:-}" ]; then
    auth=(-H "X-Dev-Login-Token: $DEV_LOGIN_TOKEN")
  fi

  curl -fsS -o /dev/null -X POST "$BASE/auth/dev-login?key=bidder-$1" \
    ${auth[@]+"${auth[@]}"} -c "$jar" || return 1
  curl -fsS -o /dev/null -X POST "$BASE/auction-rooms/share/$code/agreement" -b "$jar" || return 1
}
export -f seed_one_buyer
export BASE WORK
# 자식 셸에서도 헤더를 붙이려면 넘겨야 한다. 안 넘기면 구매자 시딩만 전부 401 이 된다.
export DEV_LOGIN_TOKEN="${DEV_LOGIN_TOKEN:-}"

# 실패한 사람 수를 센다. xargs 는 자식이 실패하면 123 으로 끝나지만 몇 명인지는 안 알려준다.
FAIL_LOG="$WORK/failed"
: >"$FAIL_LOG"
# "번호 방코드" 를 한 줄씩 만들어 넘긴다. 배열은 export 가 안 돼서 부모가 미리 짝지어 준다.
buyer_list() {
  local i=1
  while [ "$i" -le "$USERS" ]; do
    printf '%s %s\n' "$i" "${ROOM_CODES[$(( (i - 1) % ROOM_COUNT ))]}"
    i=$((i + 1))
  done
}

buyer_list \
  | xargs -P "$CONCURRENCY" -n 2 bash -c 'seed_one_buyer "$1" "$2" || echo "$1" >>"'"$FAIL_LOG"'"' _ \
  || true

FAILED="$(wc -l <"$FAIL_LOG" | tr -d ' ')"
if [ "$FAILED" != "0" ]; then
  echo "[seed] 구매자 ${FAILED}명이 로그인 또는 약관 동의에 실패했다." >&2
  echo "       이대로 재면 그 사람들의 입찰이 전부 거절돼 숫자가 의미 없어진다. 여기서 멈춘다." >&2
  exit 1
fi

# 결과 조회 부하 시나리오(#329) 전용. 낙찰자·낙찰 후보를 실제 도메인 경로로 만들어야
# GET .../results 가 SOLD 물품과 myRank 를 채운 채로 응답한다.
if [ "$CLOSE_ROOM" -eq 1 ]; then
  if [ "$START_MODE" != "all" ]; then
    echo "--close-room 은 --start all 이어야 한다 (입찰은 IN_PROGRESS 물품에만 들어간다)." >&2
    exit 1
  fi
  if [ "$ROOM_COUNT" -ne 1 ]; then
    echo "--close-room 은 방 하나짜리 시딩에서만 쓴다 (--per-room 을 빼거나 0으로 둔다)." >&2
    exit 1
  fi
  if [ "$BIDS_PER_ITEM" -gt "$USERS" ] 2>/dev/null; then
    echo "--bids-per-item($BIDS_PER_ITEM)이 --users($USERS)보다 크다. 로그인·동의된 구매자가 모자란다." >&2
    exit 1
  fi

  echo "[seed] 결과 조회용 입찰 (물품당 ${BIDS_PER_ITEM}건, 증가하는 금액)"
  # 물품마다 같은 구매자 1..N 을 재사용하면 안 된다. 입찰 Rate Limiter(#324, 버킷
  # 용량 5)가 짧은 시간에 여러 물품을 잇달아 입찰한 회원을 7009 로 거절한다 — 실측으로
  # 물품 6개째부터 막혔다. 그래서 입찰마다 다른 구매자를 돌려 쓴다. 한 사람이 한 번만
  # 입찰하면 버킷을 건드릴 일이 없다.
  BIDDER_SEQ=0
  for item_id in $(printf '%s' "$STARTED_IDS" | tr ',' ' '); do
    PRICE="$START_PRICE"
    b=1
    while [ "$b" -le "$BIDS_PER_ITEM" ]; do
      PRICE=$((PRICE + UNIT))
      BIDDER_SEQ=$((BIDDER_SEQ + 1))
      BIDDER=$(( (BIDDER_SEQ - 1) % USERS + 1 ))
      # 위 구매자 시딩이 이미 로그인·동의까지 끝낸 쿠키를 그대로 쓴다. 새로 만들 이유가 없다.
      api "$WORK/buyer-$BIDDER.cookie" POST "/auction-items/$item_id/bids" \
        "$(jq -nc --argjson amount "$PRICE" '{amount:$amount}')" >/dev/null
      b=$((b + 1))
    done
  done

  echo "[seed] 경매방 종료 (진행 중 물품을 전부 닫는다)"
  api "$SELLER_JAR" POST "/auction-rooms/$ROOM_ID/close" >/dev/null
fi

echo "[seed] 완료"

# run.sh 가 이 파일을 source 한다. **값을 반드시 따옴표로 감싼다.**
# ROOM_ITEM_IDS 는 방 사이를 ; 로 나누는데, 따옴표가 없으면 셸이 그걸 명령 구분자로 읽어서
# 뒷부분을 명령으로 실행하려다 죽는다 (실측: "4,5,6: command not found").
emit() {
  echo "SHARE_CODE='$SHARE_CODE'"
  echo "ROOM_ID='$ROOM_ID'"
  echo "ITEM_IDS='$STARTED_IDS'"
  echo "CLOSE_ITEM_IDS='$READY_IDS'"
  # 방이 여럿일 때 k6 가 VU 를 방에 배정하는 데 쓴다. 방 하나면 값이 하나씩만 들어간다.
  echo "SHARE_CODES='$(IFS=,; echo "${ROOM_CODES[*]}")'"
  echo "ROOM_ITEM_IDS='$ROOM_ITEM_GROUPS'"
  echo "ROOM_COUNT='$ROOM_COUNT'"
  echo "SEEDED_USERS='$USERS'"
  echo "START_PRICE='$START_PRICE'"
  echo "BID_UNIT='$UNIT'"
}

if [ -n "$OUT" ]; then
  emit >"$OUT"
else
  emit
fi
