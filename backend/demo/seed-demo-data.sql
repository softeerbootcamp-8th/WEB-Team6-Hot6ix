-- =============================================================================
-- 시연용 경매방과 물품 넣기 (이슈 #333)
--
--   mysql -h {host} -u {user} -p --default-character-set=utf8mb4 {db} \
--       < backend/demo/seed-demo-data.sql
--
-- 넣는 것: 경매방 3개, 상품 24개, 경매 물품 24개(방마다 진행 중 3개 + 대기 5개),
-- 진행 중 물품의 입찰 27건. 알림 피드는 이 스크립트가 아니라 seed-events.py 가 채운다.
-- 지울 때는 backend/demo/cleanup-demo-data.sql 을 돌린다.
--
-- 판매자 계정과 판매자 프로필은 이 스크립트가 만들지 않는다. 먼저 카카오로 로그인해서
-- 계정을 만들고 판매자 등록까지 화면에서 마친 뒤, 아래 @seller_store_name 을 채운다.
-- =============================================================================

-- 0. 시간대 --------------------------------------------------------------------
--
-- 앱은 컨테이너 TZ 가 Asia/Seoul 이라 LocalDateTime 을 KST 벽시계로 쓴다(deploy 의
-- docker-compose.yml). 반면 MySQL 세션은 서버 시각을 그대로 쓰는데 RDS 도 로컬 컨테이너도
-- UTC 라, 그냥 NOW() 를 쓰면 9시간 뒤처진 값이 들어간다. 그러면 12시간짜리로 넣은 물품이
-- 앱 기준으로는 3시간 뒤에 닫힌다. 실제로 로컬에서 그렇게 나왔다.
SET time_zone = '+09:00';

-- 1. 채워야 하는 값 ----------------------------------------------------------

-- 판매자 등록할 때 입력한 가게 이름 (seller_profiles.store_name)
SET @seller_store_name = '업비드 스토어';

-- 진행 중 물품이 몇 시간 뒤에 닫힐지. 12시간이 상한이다 -- 화면과 API가 경매 시간을
-- 720분까지만 받으므로(AuctionItemStartRequestDto), 더 길게 넣으면 화면에서는 만들 수
-- 없는 물품이 된다.
--
-- 이 12시간이 지나 진행 중 물품이 닫혀도 방은 계속 열려 있다. 대기 물품이 하나라도
-- 남아 있으면 방치된 방 자동 종료(12시간) 대상에서 빠지기 때문이다. 다시 입찰을
-- 받으려면 판매자 화면에서 대기 물품을 시작하면 된다.
SET @auction_hours = 12;

-- 방 공통 설정. 입찰 단위는 방마다 달라서 아래 3번의 방 표에 있다.
SET @soft_close_trigger_seconds = 60;
SET @soft_close_extend_seconds = 60;

-- 진행 중 물품에 미리 입찰 내역을 넣을지. 0 이면 입찰 없이 시작가 그대로 둔다.
--
-- 1 이면 입찰자 6명을 만들고 진행 중 물품마다 3건씩 넣는다. 리더보드가 상위 3명을
-- 보여주므로 방에 들어오자마자 순위표가 차 있다.
--
-- **입찰까지 넣을 거면 반드시 처음 넣을 때 같이 넣어야 한다.** Redis 에 그 물품 상태가
-- 한 번 만들어지면 나중에 MySQL 을 고쳐도 Redis 는 덮어쓰지 않는다. 옛 스냅샷으로
-- 되돌리지 않으려고 일부러 그렇게 돼 있다 (AuctionRedisStore.seed).
SET @seed_bids = 1;

SET @now = NOW(6);

-- 진행 중 물품은 30분 전에 시작한 것으로 둔다. 미리 넣는 입찰이 시작 시각보다 앞서면
-- 알림 피드에 "입찰이 들어온 뒤에 경매가 시작됐다"는 순서로 찍힌다.
SET @started_at = DATE_SUB(@now, INTERVAL 30 MINUTE);
SET @end_at = DATE_ADD(@started_at, INTERVAL @auction_hours HOUR);

-- 2. 판매자 확인 --------------------------------------------------------------

-- 가게 이름이 같은 판매자가 둘 이상이면 세지 못하므로, 개수를 먼저 세서 하나일 때만
-- 고른다. 하나가 아니면 @seller_profile_id 가 NULL 로 남아 아래 가드에 걸린다.
SET @seller_match_count = (
    SELECT COUNT(*) FROM seller_profiles
    WHERE store_name = @seller_store_name AND deleted_at IS NULL
);

SET @seller_profile_id = (
    SELECT sp.seller_profile_id
    FROM seller_profiles sp
    JOIN users u ON u.user_id = sp.user_id AND u.deleted_at IS NULL
    WHERE sp.store_name = @seller_store_name
      AND sp.deleted_at IS NULL
      AND @seller_match_count = 1
);

-- 판매자를 못 찾으면 여기서 멈춘다. 없는 테이블 이름을 읽어 일부러 에러를 내는데, 에러
-- 메시지에 그 이름이 그대로 찍히므로 무엇이 잘못됐는지 바로 보인다.
--
-- IF() 안에 바로 쓰지 않고 PREPARE 로 미루는 이유는, 없는 이름은 조건을 따지기 전에
-- 파싱 단계에서 걸려서 판매자가 있어도 에러가 나기 때문이다. PREPARE 는 그 문장을
-- 실제로 준비할 때 이름을 찾으므로 조건이 거짓일 때만 터진다.
--
-- 이름을 더 길게 쓰지 못하는 것은 MySQL 식별자 한도가 64바이트여서다. 한글은 한 글자가
-- 3바이트라 스무 자를 조금 넘기면 "Identifier name is too long" 이 대신 나온다.
SET @assert_sql = IF(@seller_profile_id IS NOT NULL,
    'DO 1',
    'SELECT 1 FROM `판매자를_못_찾음_가게_이름_확인`');
PREPARE assert_seller FROM @assert_sql;
EXECUTE assert_seller;
DEALLOCATE PREPARE assert_seller;

DROP TEMPORARY TABLE IF EXISTS demo_seed_rooms;
DROP TEMPORARY TABLE IF EXISTS demo_seed_items;

START TRANSACTION;

-- 3. 넣을 내용 ---------------------------------------------------------------
--
-- 문구와 사진 URL은 이 두 표만 고치면 된다. 아래 4번부터는 손댈 일이 없다.
--
--   name        상품 이름 (30자 이하, 24개가 서로 겹치면 안 된다)
--   description 상품 소개 (100자 이하)
--   image_url   판매자 화면으로 사진을 올리면 나오는 S3 URL
--   status      IN_PROGRESS(들어오자마자 입찰 가능) 또는 READY(판매자가 시작해야 열림)
--
-- 한 방에 IN_PROGRESS 는 3개까지다 (MAX_IN_PROGRESS_PER_ROOM, 운영 기본값 3).

CREATE TEMPORARY TABLE demo_seed_rooms (
    room_no         TINYINT      NOT NULL PRIMARY KEY,
    share_code      VARCHAR(32)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     TEXT         NULL,
    cover_image_url TEXT         NULL,
    live_url        TEXT         NULL,
    bid_increment   BIGINT       NOT NULL,
    auction_room_id BIGINT       NULL
);

-- 입찰 단위는 물건 값에 맞춘다. 35만원짜리 스니커즈에 1,000원씩 올리면 화면에서 값이
-- 움직이는 게 보이지 않는다.
INSERT INTO demo_seed_rooms (room_no, share_code, name, description, cover_image_url, live_url, bid_increment) VALUES
(1, 'x2RiepPnNioPZliA', '빈티지 셀렉트샵 라이브',
    '90년대 유럽에서 직접 골라온 옷들입니다. 사이즈는 실측으로 안내드려요.',
    'https://images.unsplash.com/photo-1494795817488-f572f2a30c87?w=800&q=80&auto=format&fit=crop', NULL, 1000),
(2, '9pgvjVqW9KdBLd7x', '한정판 스니커즈 경매',
    '박스와 택 그대로 보관한 한정판만 올립니다. 정품 확인 후 발송합니다.',
    'https://images.unsplash.com/photo-1527090526205-beaac8dc3c62?w=800&q=80&auto=format&fit=crop', NULL, 10000),
(3, 'Wg10FGdlRdY2Vu16', '작가님 도자기 공방',
    '하나씩 물레로 빚어 구운 그릇입니다. 같은 모양이 두 개 없습니다.',
    'https://images.unsplash.com/photo-1528466829416-7c2576152a09?w=800&q=80&auto=format&fit=crop', NULL, 1000);

CREATE TEMPORARY TABLE demo_seed_items (
    room_no        TINYINT      NOT NULL,
    item_no        TINYINT      NOT NULL,
    name           VARCHAR(30)  NOT NULL,
    description    VARCHAR(100) NULL,
    image_url      TEXT         NULL,
    starting_price BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    product_id     BIGINT       NULL,
    PRIMARY KEY (room_no, item_no)
);

INSERT INTO demo_seed_items (room_no, item_no, name, description, image_url, starting_price, status) VALUES
-- 1번 방: 빈티지 셀렉트샵
(1, 1, '클래식 트렌치코트 95', '90년대 영국 생산 제품입니다. 안감 체크 살아 있고 벨트 그대로 있습니다.', 'https://images.unsplash.com/photo-1550872199-63f4382fe925?w=800&q=80&auto=format&fit=crop', 120000, 'IN_PROGRESS'),
(1, 2, '리바이스 501 빅E 32인치', '빅E 라벨 그대로입니다. 밑단 원단 남아 있고 수선 자국 없습니다.', 'https://images.unsplash.com/photo-1547227795-33be3f89c971?w=800&q=80&auto=format&fit=crop', 90000, 'IN_PROGRESS'),
(1, 3, '해리스 트위드 자켓 100', '스코틀랜드 원단 라벨 있습니다. 어깨 47cm 실측입니다.', 'https://images.unsplash.com/photo-1649937408746-4d2f603f91c8?w=800&q=80&auto=format&fit=crop', 70000, 'IN_PROGRESS'),
(1, 4, '아란 니트 스웨터 free', '손뜨개 아란 패턴입니다. 늘어남 없이 도톰합니다.', 'https://images.unsplash.com/photo-1574201635302-388dd92a4c3f?w=800&q=80&auto=format&fit=crop', 45000, 'READY'),
(1, 5, '가죽 더플백 브라운', '이태리 소가죽입니다. 손잡이 마감 튼튼하고 안쪽 깨끗합니다.', 'https://images.unsplash.com/photo-1479219136056-56bb6495a005?w=800&q=80&auto=format&fit=crop', 80000, 'READY'),
(1, 6, '울 니트 머플러', '램스울 100% 입니다. 보풀 거의 없습니다.', 'https://images.unsplash.com/photo-1491245257527-395e9c480145?w=800&q=80&auto=format&fit=crop', 25000, 'READY'),
(1, 7, '데님 워크 자켓 L', '80년대 워크웨어입니다. 페이딩이 자연스럽게 들어가 있습니다.', 'https://images.unsplash.com/photo-1495105787522-5334e3ffa0ef?w=800&q=80&auto=format&fit=crop', 60000, 'READY'),
(1, 8, '코듀로이 팬츠 30인치', '두꺼운 골 코듀로이입니다. 밑위 넉넉해서 편합니다.', 'https://images.unsplash.com/photo-1718252540511-e958742e4165?w=800&q=80&auto=format&fit=crop', 35000, 'READY'),
-- 2번 방: 한정판 스니커즈
(2, 1, '조던 레트로 화이트 270', '미착용 새 제품입니다. 박스와 여분 끈 모두 있습니다.', 'https://images.unsplash.com/photo-1506544777-64cfbe1142df?w=800&q=80&auto=format&fit=crop', 350000, 'IN_PROGRESS'),
(2, 2, '덩크로우 파나틱스 265', '두 번 신었습니다. 아웃솔 깨끗하고 접힘 거의 없습니다.', 'https://images.unsplash.com/photo-1584735174914-6b1272458e3e?w=800&q=80&auto=format&fit=crop', 180000, 'IN_PROGRESS'),
(2, 3, '뉴발란스 992 그레이 275', '미국 생산 제품입니다. 밑창 교체 없이 그대로입니다.', 'https://images.unsplash.com/photo-1542272604-78d13c1f741a?w=800&q=80&auto=format&fit=crop', 220000, 'IN_PROGRESS'),
(2, 4, '이지 350 v2 옐로우 260', '정품 확인 카드 동봉합니다. 박스 상태 좋습니다.', 'https://images.unsplash.com/photo-1548369735-f548cbe6a294?w=800&q=80&auto=format&fit=crop', 300000, 'READY'),
(2, 5, '삼바 OG 크림 265', '올해 구입했고 실착 세 번입니다.', 'https://images.unsplash.com/photo-1534211269469-314ffbac5b34?w=800&q=80&auto=format&fit=crop', 90000, 'READY'),
(2, 6, '에어포스1 하이 270', '새 제품입니다. 박스 모서리만 눌렸습니다.', 'https://images.unsplash.com/photo-1512374382149-233c42b6a83b?w=800&q=80&auto=format&fit=crop', 110000, 'READY'),
(2, 7, '척테일러 70 하이 275', '캔버스 오염 없습니다. 인솔 그대로입니다.', 'https://images.unsplash.com/photo-1527128296579-fce16948f060?w=800&q=80&auto=format&fit=crop', 70000, 'READY'),
(2, 8, '가젤 인디고 260', '박스 없이 신발만 보냅니다. 상태는 사진 참고해주세요.', 'https://images.unsplash.com/photo-1572026174154-97194f1e3567?w=800&q=80&auto=format&fit=crop', 60000, 'READY'),
-- 3번 방: 도자기 공방
(3, 1, '분청 밥그릇', '물레로 빚어 장작 가마에서 구웠습니다. 전자레인지 사용 가능합니다.', 'https://images.unsplash.com/photo-1510035618584-c442b241abe7?w=800&q=80&auto=format&fit=crop', 40000, 'IN_PROGRESS'),
(3, 2, '청화 백자 항아리 소', '높이 24cm입니다. 손으로 그린 무늬라 같은 것이 없습니다.', 'https://images.unsplash.com/photo-1559740983-b9c5ffbac1ff?w=800&q=80&auto=format&fit=crop', 250000, 'IN_PROGRESS'),
(3, 3, '무광 머그컵', '한 점씩 물레로 뽑았습니다. 손잡이가 두툼해서 잡기 편합니다.', 'https://images.unsplash.com/photo-1506372023823-741c83b836fe?w=800&q=80&auto=format&fit=crop', 55000, 'IN_PROGRESS'),
(3, 4, '청자 찻주전자', '거름망까지 흙으로 빚었습니다. 물줄기 끊김 없습니다.', 'https://images.unsplash.com/photo-1495414975755-d9ecd3e6d729?w=800&q=80&auto=format&fit=crop', 120000, 'READY'),
(3, 5, '옹기 저장 항아리 중', '숨 쉬는 옹기입니다. 장이나 김치 보관용으로 쓰시면 됩니다.', 'https://images.unsplash.com/photo-1510828561531-05a3388f6d3d?w=800&q=80&auto=format&fit=crop', 80000, 'READY'),
(3, 6, '기하무늬 접시 4장', '지름 21cm입니다. 겹쳐 보관해도 유약이 상하지 않습니다.', 'https://images.unsplash.com/photo-1499028203764-8669cfd05719?w=800&q=80&auto=format&fit=crop', 65000, 'READY'),
(3, 7, '재유 화병 대', '가마 안 재가 그대로 내려앉아 색이 한 점씩 다릅니다.', 'https://images.unsplash.com/photo-1481401908818-600b7a676c0d?w=800&q=80&auto=format&fit=crop', 150000, 'READY'),
(3, 8, '무늬 국그릇', '입구가 넓어 국이나 면 요리에 두루 씁니다.', 'https://images.unsplash.com/photo-1589897846179-2b0367f38402?w=800&q=80&auto=format&fit=crop', 35000, 'READY');

-- 4. 경매방 --------------------------------------------------------------------

INSERT INTO auction_rooms (
    seller_profile_id, name, cover_image_url, description, live_url, share_code, status,
    bid_increment, soft_close_trigger_seconds, soft_close_extend_seconds,
    closed_at, created_at, updated_at, deleted_at
)
SELECT @seller_profile_id, r.name, r.cover_image_url, r.description, r.live_url, r.share_code, 'OPEN',
       r.bid_increment, @soft_close_trigger_seconds, @soft_close_extend_seconds,
       NULL, @now, @now, NULL
FROM demo_seed_rooms r
ORDER BY r.room_no;

UPDATE demo_seed_rooms r
JOIN auction_rooms a ON a.share_code = r.share_code
SET r.auction_room_id = a.auction_room_id;

-- 5. 상품 ----------------------------------------------------------------------
--
-- products 에는 자연 키가 없어서, 방금 넣은 행만 골라내려고 created_at 이 정확히 @now 인
-- 것으로 거른다. 이 스크립트가 넣은 24개만 걸린다.

INSERT INTO products (
    seller_profile_id, name, description, image_url, reference_url,
    created_at, updated_at, deleted_at
)
SELECT @seller_profile_id, i.name, i.description, i.image_url, NULL,
       @now, @now, NULL
FROM demo_seed_items i
ORDER BY i.room_no, i.item_no;

UPDATE demo_seed_items i
JOIN products p
  ON p.seller_profile_id = @seller_profile_id
 AND p.created_at = @now
 AND p.name = i.name
SET i.product_id = p.product_id;

-- 6. 경매 물품 -------------------------------------------------------------------
--
-- 진행 중 물품은 AuctionItem.start 가 세우는 값과 같게 넣는다. 시작 시각과 마감 시각을
-- 채우고 original_end_at 을 end_at 과 같은 값으로 둔다. 대기 물품은 셋 다 비운다.

INSERT INTO auction_items (
    auction_room_id, product_id, leader_user_id, starting_price, bid_increment, current_price,
    status, started_at, original_end_at, end_at, total_extension_seconds, notified_at,
    created_at, updated_at
)
SELECT r.auction_room_id, i.product_id, NULL, i.starting_price, r.bid_increment, i.starting_price,
       i.status,
       IF(i.status = 'IN_PROGRESS', @started_at, NULL),
       IF(i.status = 'IN_PROGRESS', @end_at, NULL),
       IF(i.status = 'IN_PROGRESS', @end_at, NULL),
       0, NULL,
       @now, @now
FROM demo_seed_items i
JOIN demo_seed_rooms r ON r.room_no = i.room_no
ORDER BY i.room_no, i.item_no;

-- 7. 입찰 내역 (@seed_bids = 1 일 때만) ---------------------------------------------
--
-- 입찰자는 SQL 로 만든 계정이라 로그인은 안 된다. 리더보드에 이름이 보이는 용도다.
-- 카카오 회원번호는 숫자라 'demo-bidder-N' 과 겹치지 않는다.

CREATE TEMPORARY TABLE demo_seed_bidders (
    bidder_no   TINYINT      NOT NULL PRIMARY KEY,
    provider_id VARCHAR(100) NOT NULL,
    nickname    VARCHAR(20)  NOT NULL,
    user_id     BIGINT       NULL
);

INSERT INTO demo_seed_bidders (bidder_no, provider_id, nickname) VALUES
(1, 'demo-bidder-1', '민지'),
(2, 'demo-bidder-2', '해나'),
(3, 'demo-bidder-3', '준서'),
(4, 'demo-bidder-4', '도윤'),
(5, 'demo-bidder-5', '서아'),
(6, 'demo-bidder-6', '지훈');

-- 이미 있으면 다시 만들지 않는다. 정리한 뒤 다시 넣을 때 계정이 두 벌 생기지 않게 한다.
INSERT INTO users (provider, provider_id, nickname, created_at, updated_at)
SELECT 'KAKAO', b.provider_id, b.nickname, @now, @now
FROM demo_seed_bidders b
LEFT JOIN users u ON u.provider = 'KAKAO' AND u.provider_id = b.provider_id
WHERE u.user_id IS NULL
  AND @seed_bids = 1;

UPDATE demo_seed_bidders b
JOIN users u ON u.provider = 'KAKAO' AND u.provider_id = b.provider_id
SET b.user_id = u.user_id;

-- 약관 동의가 없으면 Redis Seed 의 참여자 목록에 안 들어간다.
UPDATE users
SET deleted_at = NULL, updated_at = @now
WHERE provider = 'KAKAO'
  AND provider_id LIKE 'demo-bidder-%'
  AND deleted_at IS NOT NULL
  AND @seed_bids = 1;

INSERT INTO auction_participants (agreed_at, auction_room_id, user_id, terms_version)
SELECT @now, r.auction_room_id, b.user_id, 'v1'
FROM demo_seed_rooms r
JOIN demo_seed_bidders b
WHERE @seed_bids = 1
  AND b.user_id IS NOT NULL;

-- 물품마다 3건. 금액은 시작가에서 입찰 단위만큼 세 번 올린 값이고, 같은 물품에 같은
-- 사람이 연달아 최고가가 되지 않도록 입찰자를 돌려 쓴다.
INSERT INTO bids (request_id, accepted_at, amount, auction_item_id, bidder_user_id)
SELECT CONCAT('demo-seed-', ai.auction_item_id, '-', s.seq),
       DATE_SUB(@now, INTERVAL (4 - s.seq) * 7 MINUTE),
       ai.starting_price + s.seq * ai.bid_increment,
       ai.auction_item_id,
       b.user_id
FROM demo_seed_items i
JOIN auction_items ai ON ai.product_id = i.product_id
JOIN (SELECT 1 AS seq UNION ALL SELECT 2 UNION ALL SELECT 3) s
JOIN demo_seed_bidders b ON b.bidder_no = MOD(ai.auction_item_id + s.seq, 6) + 1
WHERE @seed_bids = 1
  AND i.status = 'IN_PROGRESS';

-- Redis Seed 가 현재가와 최고 입찰자를 여기서 읽어간다. 이 값이 bids 와 어긋나면
-- 화면과 입찰 판정이 갈린다.
UPDATE auction_items ai
JOIN demo_seed_items i ON i.product_id = ai.product_id
JOIN bids top ON top.auction_item_id = ai.auction_item_id
SET ai.current_price = top.amount,
    ai.leader_user_id = top.bidder_user_id,
    ai.updated_at = @now
WHERE @seed_bids = 1
  AND i.status = 'IN_PROGRESS'
  AND top.amount = ai.starting_price + 3 * ai.bid_increment;

-- 8. 확인 ------------------------------------------------------------------------

SELECT r.share_code,
       a.name                         AS `경매방`,
       a.status                       AS `방 상태`,
       a.bid_increment                AS `입찰 단위`,
       COUNT(DISTINCT CASE WHEN ai.status = 'IN_PROGRESS' THEN ai.auction_item_id END) AS `진행 중`,
       COUNT(DISTINCT CASE WHEN ai.status = 'READY' THEN ai.auction_item_id END)       AS `대기`,
       COUNT(b.bid_id)                                                                 AS `입찰`,
       MIN(ai.end_at)                                                                  AS `마감 시각`
FROM demo_seed_rooms r
JOIN auction_rooms a ON a.auction_room_id = r.auction_room_id
JOIN auction_items ai ON ai.auction_room_id = r.auction_room_id
LEFT JOIN bids b ON b.auction_item_id = ai.auction_item_id
GROUP BY r.share_code, a.name, a.status, a.bid_increment
ORDER BY r.share_code;

COMMIT;
