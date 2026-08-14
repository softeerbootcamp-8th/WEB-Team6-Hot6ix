-- 마감 임박 알림이 마지막으로 나간 시각. 같은 물품에 알림이 두 번 나가는 것을 막는다 (#290).
-- NULL 은 아직 알린 적이 없다는 뜻이다. 기존 행은 전부 그렇게 본다.
ALTER TABLE `auction_items`
    ADD COLUMN `notified_at` datetime(6) DEFAULT NULL;

-- 낙찰 후보를 순위대로 훑는 조회용 인덱스 (#313).
CREATE INDEX idx_dc_auction_status_rank
    ON deal_candidates (auction_item_id, status, candidate_rank);
