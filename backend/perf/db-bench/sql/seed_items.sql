-- auction_items 를 @from 부터 @to 까지 채운다. bench.sh 가 배치로 나눠 여러 번 부른다.
--
-- 상태 분포를 실제와 맞춘다. 끝난 물품(SOLD, FAILED)은 지우지 않아 계속 쌓이고, 진행 중인
-- 물품은 항상 몇 건뿐이며 가장 최근에 만들어진 것들이다. 그래서 뒤쪽 @in_progress 개를
-- IN_PROGRESS 로, 그 앞 @ready 개를 아직 시작 안 한 READY 로 둔다.
--
-- READY 는 end_at 이 NULL 이다. 재는 쿼리의 `end_at is not null` 조건이 실제로 걸러 내는
-- 행이 있어야 조건 하나가 공짜로 취급되지 않는다.

INSERT INTO auction_items (auction_item_id, auction_room_id, product_id, status,
                           starting_price, current_price, bid_increment, total_extension_seconds,
                           started_at, end_at, original_end_at, notified_at,
                           created_at, updated_at)
WITH RECURSIVE n AS (
    SELECT @from AS i
    UNION ALL
    SELECT i + 1 FROM n WHERE i < @to
),
item AS (
    SELECT i,
           CASE
               WHEN i > @items - @in_progress THEN 'IN_PROGRESS'
               WHEN i > @items - @in_progress - @ready THEN 'READY'
               WHEN i % 10 = 0 THEN 'FAILED'
               ELSE 'SOLD'
           END AS status,
           CASE
               WHEN i > @items - @in_progress
                   THEN @now + INTERVAL (i - (@items - @in_progress)) MINUTE
               WHEN i > @items - @in_progress - @ready THEN NULL
               ELSE @now - INTERVAL (@items - i) SECOND
           END AS end_at
    FROM n
)
SELECT i,
       1 + (i % @rooms),
       1 + (i % @products),
       status,
       10000, 10000, 1000, 0,
       end_at - INTERVAL 5 MINUTE,
       end_at,
       end_at,
       NULL,
       @now, @now
FROM item;
