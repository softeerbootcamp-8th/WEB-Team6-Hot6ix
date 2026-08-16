#!/usr/bin/env bash
#
# perf 스택이 떠 있는 상태에서, 앱이 10초마다 돌리는 마감 예약 재동기화 조회를
# 실행 하나 단위로 모은다. (이슈 #335)
#
#   ./app-sample.sh 180        # 180초 동안 지켜본다
#
# 다이제스트 표는 앱이 뜬 뒤로 계속 누적된 값이라, 시딩 구간처럼 조건이 다른 실행이 섞여
# 있으면 평균이 통째로 흔들린다. 그래서 짧은 간격으로 두 번 읽어 그 사이 차이만 본다.
# 차이를 실행 횟수로 나누면 그 구간에서 한 번에 몇 ms 걸렸는지가 나온다.
#
# 출력은 한 줄이 한 구간이다. TSV 라 그대로 표에 붙일 수 있다.

set -euo pipefail

cd "$(dirname "$0")/.."

SECONDS_TOTAL="${1:-180}"
INTERVAL="${2:-10}"

MYSQL=(docker compose -f docker-compose.perf.yml exec -T mysql
       mysql -uroot -p1234 -N -B upbid -e)

# 누적값 세 개를 한 줄로 받는다. 실행 횟수, 총 시간(ps), 총 읽은 행.
snapshot() {
    "${MYSQL[@]}" "
      SELECT COALESCE(SUM(COUNT_STAR), 0), COALESCE(SUM(SUM_TIMER_WAIT), 0),
             COALESCE(SUM(SUM_ROWS_EXAMINED), 0)
      FROM performance_schema.events_statements_summary_by_digest
      WHERE DIGEST_TEXT LIKE 'SELECT%'
        AND DIGEST_TEXT LIKE '%auction_items%'
        AND DIGEST_TEXT LIKE '%soft_close_trigger_seconds%'
        AND DIGEST_TEXT LIKE '%order by%';" 2>/dev/null
}

printf '구간\t실행\t실행당 ms\t실행당 읽은 행\n'

read -r PREV_N PREV_T PREV_R <<< "$(snapshot)"
ELAPSED=0
WINDOW=0

while [ "$ELAPSED" -lt "$SECONDS_TOTAL" ]; do
    sleep "$INTERVAL"
    ELAPSED=$(( ELAPSED + INTERVAL ))
    WINDOW=$(( WINDOW + 1 ))

    read -r N T R <<< "$(snapshot)"
    DN=$(( N - PREV_N ))

    if [ "$DN" -gt 0 ]; then
        awk -v w="$WINDOW" -v dn="$DN" -v dt="$(( T - PREV_T ))" -v dr="$(( R - PREV_R ))" \
            'BEGIN { printf "%d\t%d\t%.3f\t%d\n", w, dn, dt / dn / 1000000000, dr / dn }'
    else
        printf '%d\t0\t-\t-\n' "$WINDOW"
    fi

    PREV_N=$N; PREV_T=$T; PREV_R=$R
done
