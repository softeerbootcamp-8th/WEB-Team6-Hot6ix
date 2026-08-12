#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUN="$ROOT/perf/run.sh"
BURST="$ROOT/perf/k6/burst.js"
BID="$ROOT/perf/k6/bid.js"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

[ -f "$BURST" ] || fail "burst.js가 없습니다"
grep -q "executor: 'per-vu-iterations'" "$BURST" || fail "per-vu-iterations를 사용하지 않습니다"
grep -q 'iterations: 1' "$BURST" || fail "VU당 1회 실행이 아닙니다"
grep -q 'prepareBid' "$BURST" || fail "동시 출발 전에 로그인·상세 조회를 준비하지 않습니다"
grep -q 'submitBid' "$BURST" || fail "공통 입찰 결과 분류를 재사용하지 않습니다"
grep -q 'BURST_MODE' "$BURST" || fail "동일 금액·증가 금액 모드를 선택할 수 없습니다"
grep -q 'export function prepareBid' "$BID" || fail "bid.js가 준비 단계를 제공하지 않습니다"
grep -q 'export function submitBid' "$BID" || fail "bid.js가 제출 단계를 제공하지 않습니다"
grep -q 'roomOfVu' "$BID" || fail "준비 단계가 VU에 배정된 경매방을 선택하지 않습니다"
grep -q 'agree(room.code)' "$BID" || fail "준비 단계가 VU에 배정된 경매방 약관에 동의하지 않습니다"
grep -q 'room.itemIds' "$BID" || fail "준비 단계가 VU에 배정된 경매방 물품만 선택하지 않습니다"
grep -q '8) SCRIPT="burst.js"' "$RUN" || fail "run.sh가 시나리오 8을 선택하지 않습니다"
grep -q '\-e BURST_MODE' "$RUN" || fail "run.sh가 BURST_MODE를 k6에 전달하지 않습니다"

echo "PASS: 동시 출발 시나리오 계약"
