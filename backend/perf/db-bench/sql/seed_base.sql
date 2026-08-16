-- 물품이 매달릴 뼈대다. 판매자 하나에 경매방 @rooms 개, 상품 @products 개를 만든다.
-- 물품 수는 bench.sh 가 @items 로 넣는다.
--
-- 상품을 물품 수만큼 만들지 않고 돌려 쓰는 이유는 재는 쿼리가 products 를 조인하지 않기
-- 때문이다. 100만 건이면 상품까지 100만 행을 넣게 되는데 그 시간이 결과에 주는 것이 없다.

SET @now = NOW(6);

INSERT INTO users (user_id, nickname, email, created_at, updated_at)
VALUES (1, '벤치 판매자', 'bench@upbid.local', @now, @now);

INSERT INTO seller_profiles (seller_profile_id, user_id, store_name, created_at, updated_at)
VALUES (1, 1, '벤치 상점', @now, @now);

INSERT INTO products (product_id, seller_profile_id, name, created_at, updated_at)
WITH RECURSIVE n AS (
    SELECT 1 AS i
    UNION ALL
    SELECT i + 1 FROM n WHERE i < @products
)
SELECT i, 1, CONCAT('벤치 상품 ', i), @now, @now FROM n;

-- soft_close_trigger_seconds 를 채워 둔다. 재는 쿼리가 이 값 때문에 경매방을 조인한다.
INSERT INTO auction_rooms (auction_room_id, seller_profile_id, bid_increment, name, share_code,
                           status, soft_close_trigger_seconds, soft_close_extend_seconds,
                           created_at, updated_at)
WITH RECURSIVE n AS (
    SELECT 1 AS i
    UNION ALL
    SELECT i + 1 FROM n WHERE i < @rooms
)
SELECT i, 1, 1000, CONCAT('벤치 방 ', i), CONCAT('bench', LPAD(i, 10, '0')),
       'OPEN', 30, 30, @now, @now
FROM n;
