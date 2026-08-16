-- 마감 예약 재동기화가 진행중 물품을 찾는 조회용 인덱스 (#335).
--
-- AuctionRecoveryRunner 가 10초마다(#327) 도는데 status 에 인덱스가 없어서 표 전체를
-- 훑고 있었다. 진행중 물품은 항상 몇 건뿐인데 끝난 물품(SOLD, FAILED)은 지우지 않아
-- 계속 쌓이므로, 몇 건 찾자고 읽는 행이 표와 함께 계속 늘어나는 구조였다.
--
-- end_at 을 두 번째 열로 둔 것은 같은 조회의 `order by end_at` 까지 받아내기 위해서다.
-- 인덱스를 status 하나로만 만들면 진행중 물품을 찾은 뒤 정렬을 따로 하게 된다.
--
-- ALGORITHM 과 LOCK 사이에 쉼표를 찍지 않는다. ALTER TABLE 은 쉼표로 이어 쓰지만
-- CREATE INDEX 는 문법 오류가 난다(MySQL 8.4.9 에서 확인).
CREATE INDEX idx_auction_items_status_end_at
    ON auction_items (status, end_at)
    ALGORITHM=INPLACE LOCK=NONE;
