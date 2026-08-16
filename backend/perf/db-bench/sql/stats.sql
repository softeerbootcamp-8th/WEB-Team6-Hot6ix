-- 방금 태그를 달고 돌린 문장들의 실행 시간과 읽은 행 수를 서버 쪽 기록에서 뽑는다.
--
-- 클라이언트에서 재지 않는 이유는 docker exec 와 접속 왕복이 쿼리 시간과 같은 자리수라
-- 그대로 섞이기 때문이다. performance_schema 는 서버가 문장 하나를 실행한 시간만 준다.
-- TIMER_WAIT 단위는 피코초라 1e9 로 나눠 ms 로 본다.
--
-- 자기 자신도 SQL_TEXT 에 태그 문자열을 그대로 담고 있어서 다음 라운드의 집계에 끼어든다.
-- 두 번째 조건이 그것을 막는다.

WITH s AS (
    SELECT TIMER_WAIT / 1000000000 AS ms,
           ROWS_EXAMINED AS rex,
           ROWS_SENT AS rsent,
           ROW_NUMBER() OVER (ORDER BY TIMER_WAIT) AS rn,
           COUNT(*) OVER () AS c
    FROM performance_schema.events_statements_history_long
    WHERE SQL_TEXT LIKE CONCAT('%bench:', @tag, '*/%')
      AND SQL_TEXT NOT LIKE '%events_statements_history_long%'
)
SELECT @tag AS tag,
       MAX(c) AS n,
       ROUND(MIN(ms), 3) AS min_ms,
       ROUND(MAX(CASE WHEN rn = CEIL(c * 0.50) THEN ms END), 3) AS p50_ms,
       ROUND(MAX(CASE WHEN rn = CEIL(c * 0.95) THEN ms END), 3) AS p95_ms,
       ROUND(MAX(CASE WHEN rn = CEIL(c * 0.99) THEN ms END), 3) AS p99_ms,
       ROUND(MAX(ms), 3) AS max_ms,
       MAX(rex) AS rows_examined,
       MAX(rsent) AS rows_sent
FROM s;
