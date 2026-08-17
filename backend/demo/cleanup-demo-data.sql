-- =============================================================================
-- 시연용 데이터 정리 (이슈 #333)
--
--   mysql -h {host} -u {user} -p --default-character-set=utf8mb4 {db} \
--       < backend/demo/cleanup-demo-data.sql
--
-- seed-demo-data.sql 이 넣은 경매방 3개와 그 방의 상품을 deleted_at 으로 가린다.
-- 행을 지우지 않으므로 잘못 돌렸으면 deleted_at 을 NULL 로 되돌리면 된다.
--
-- 지우는 기준은 share_code 세 개뿐이다. 그 방에 달리지 않은 것은 건드리지 않는다.
-- =============================================================================

-- 앱이 KST 벽시계로 쓰므로 deleted_at 도 같은 기준으로 맞춘다. 자세한 이유는
-- seed-demo-data.sql 맨 위 "시간대" 절에 적었다.
SET time_zone = '+09:00';

SET @share_code_1 = 'x2RiepPnNioPZliA';
SET @share_code_2 = '9pgvjVqW9KdBLd7x';
SET @share_code_3 = 'Wg10FGdlRdY2Vu16';

SET @now = NOW(6);

-- 1. 무엇이 지워지는지 먼저 본다 ------------------------------------------------

SELECT a.share_code,
       a.name                         AS `경매방`,
       COUNT(ai.auction_item_id)      AS `물품`,
       SUM(ai.status = 'IN_PROGRESS') AS `아직 진행 중`
FROM auction_rooms a
LEFT JOIN auction_items ai ON ai.auction_room_id = a.auction_room_id
WHERE a.share_code IN (@share_code_1, @share_code_2, @share_code_3)
  AND a.deleted_at IS NULL
GROUP BY a.share_code, a.name;

START TRANSACTION;

-- 2. 상품 ------------------------------------------------------------------------
--
-- 경매방 → 물품 → 상품으로 타고 들어간다. 그 방에 올라간 상품만 걸린다.

UPDATE products p
JOIN auction_items ai ON ai.product_id = p.product_id
JOIN auction_rooms a ON a.auction_room_id = ai.auction_room_id
SET p.deleted_at = @now,
    p.updated_at = @now
WHERE a.share_code IN (@share_code_1, @share_code_2, @share_code_3)
  AND p.deleted_at IS NULL;

-- 3. 경매방 ----------------------------------------------------------------------

UPDATE auction_rooms
SET deleted_at = @now,
    updated_at = @now
WHERE share_code IN (@share_code_1, @share_code_2, @share_code_3)
  AND deleted_at IS NULL;

-- 4. 시드용 입찰자 계정 -------------------------------------------------------------
--
-- seed 스크립트가 만든 계정만 가린다. 입찰 내역(bids)과 약관 동의(auction_participants)
-- 행은 그대로 둔다. 지우려면 낙찰 후보와 거래까지 함께 봐야 하는데, 방이 가려지면
-- 화면에서 도달할 길이 없어서 남겨도 보이지 않는다.

UPDATE users
SET deleted_at = @now,
    updated_at = @now
WHERE provider = 'KAKAO'
  AND provider_id LIKE 'demo-bidder-%'
  AND deleted_at IS NULL;

COMMIT;

-- 4. 확인 ------------------------------------------------------------------------

SELECT share_code,
       name       AS `경매방`,
       deleted_at AS `지운 시각`
FROM auction_rooms
WHERE share_code IN (@share_code_1, @share_code_2, @share_code_3);

-- =============================================================================
-- 방 공개 조회 캐시
--
-- 방을 가려도 Redis 에 남은 캐시(auction:room:public::{shareCode})가 최대 5분간 옛 정보를
-- 그대로 내준다. 기다려도 알아서 지워지지만, 바로 떼려면 서버에서 아래를 돌린다.
--
--   redis-cli DEL \
--     'auction:room:public::x2RiepPnNioPZliA' \
--     'auction:room:public::9pgvjVqW9KdBLd7x' \
--     'auction:room:public::Wg10FGdlRdY2Vu16'
--
-- 캐시만 지우고 DB 행을 안 지우면 다음 조회에서 다시 채워진다. 순서는 DB 먼저, 캐시 나중이다.
-- =============================================================================

-- =============================================================================
-- 진행 중이던 물품에 대하여
--
-- auction_items 에는 deleted_at 컬럼이 없어서 물품 행은 그대로 남는다. 방이 가려지면
-- 공유 링크와 목록에서 모두 사라지므로 화면에서는 보이지 않는다.
--
-- 다만 마감 예약과 Redis Seed 는 방이 아니라 물품 상태를 보므로, 진행 중이던 물품은
-- end_at 이 될 때까지 계속 붙들려 있다가 그때 알아서 닫힌다. 그걸 기다리지 않고 지금
-- 바로 떼려면 아래를 같이 돌린다. 입찰이 한 번이라도 들어온 물품(leader_user_id 가
-- 있는 물품)은 낙찰 처리와 어긋나므로 건드리지 않는다.
--
--   UPDATE auction_items ai
--   JOIN auction_rooms a ON a.auction_room_id = ai.auction_room_id
--   SET ai.status = 'FAILED',
--       ai.updated_at = NOW(6)
--   WHERE a.share_code IN ('x2RiepPnNioPZliA', '9pgvjVqW9KdBLd7x', 'Wg10FGdlRdY2Vu16')
--     AND ai.status = 'IN_PROGRESS'
--     AND ai.leader_user_id IS NULL;
-- =============================================================================
