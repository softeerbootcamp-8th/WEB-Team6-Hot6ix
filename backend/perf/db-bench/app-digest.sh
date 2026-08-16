#!/usr/bin/env bash
#
# perf 스택에서 앱이 실제로 던진 마감 예약 재동기화 조회의 통계를 뽑는다. (이슈 #335)
#
#   ./app-digest.sh              # run.sh 로 한 실행이 끝난 직후에 부른다
#
# db-bench 는 SQL 을 직접 돌려서 인덱스 자체의 효과를 봤고, 이쪽은 앱이 10초마다 돌린
# 그 문장을 본다. Hibernate 가 만들고 JDBC 를 거쳐 나간 문장이라 중간에 뭐가 끼어도 여기
# 숫자에 들어온다.
#
# 문장에 주석을 못 다니까 events_statements_summary_by_digest 를 쓴다. MySQL 이 문장을
# 정규화해서 모아 둔 표라 실행 횟수와 p95, p99 가 이미 들어 있고, 기본으로 켜져 있어서
# perf 스택 설정을 건드리지 않아도 된다.
#
# 값이 실행 시작부터 누적이라는 점만 주의한다. 워밍업과 시딩 구간이 섞여 있으므로 두 실행을
# 나란히 놓고 비교하는 용도로만 읽는다.

set -euo pipefail

cd "$(dirname "$0")/.."

MYSQL="docker compose -f docker-compose.perf.yml exec -T mysql mysql -uroot -p1234 -N -B upbid"

$MYSQL 2>/dev/null <<'SQL'
SELECT '재동기화 조회' AS q,
       COUNT_STAR AS n,
       ROUND(AVG_TIMER_WAIT / 1000000000, 3) AS avg_ms,
       ROUND(QUANTILE_95 / 1000000000, 3) AS p95_ms,
       ROUND(QUANTILE_99 / 1000000000, 3) AS p99_ms,
       ROUND(MAX_TIMER_WAIT / 1000000000, 3) AS max_ms,
       ROUND(SUM_ROWS_EXAMINED / COUNT_STAR) AS rows_examined_avg,
       ROUND(SUM_ROWS_SENT / COUNT_STAR, 1) AS rows_sent_avg,
       ROUND(SUM_TIMER_WAIT / 1000000000, 1) AS total_ms
FROM performance_schema.events_statements_summary_by_digest
WHERE DIGEST_TEXT LIKE 'SELECT%'
  AND DIGEST_TEXT LIKE '%auction_items%'
  AND DIGEST_TEXT LIKE '%soft_close_trigger_seconds%'
  AND DIGEST_TEXT LIKE '%order by%';

-- COUNT(*) 로 세지 않는다. 인덱스가 없는 라운드에서는 100만 행을 훑느라 5초가 걸리고,
-- 그 5초가 지금 재고 있는 대상과 같은 표를 흔든다. 통계값 어림수면 충분하다.
SELECT '물품 표(어림)' AS q, TABLE_ROWS AS rows_total
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'upbid' AND TABLE_NAME = 'auction_items';

SELECT '인덱스' AS q, COUNT(*) AS present
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'upbid' AND TABLE_NAME = 'auction_items'
  AND INDEX_NAME = 'idx_auction_items_status_end_at';

SELECT '적용된 마이그레이션' AS q, GROUP_CONCAT(version ORDER BY installed_rank) AS versions
FROM flyway_schema_history WHERE success = 1;
SQL
