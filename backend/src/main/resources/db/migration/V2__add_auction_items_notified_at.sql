-- 마감 임박 알림이 마지막으로 나간 시각. 같은 물품에 알림이 두 번 나가는 것을 막는다 (#290).
--
-- 단순 플래그가 아니라 시각인 것은 Soft Close 연장 때문이다. 연장되면 알림 시각
-- (end_at - soft_close_trigger_seconds)도 함께 밀리므로, 이 값이 그보다 앞서면 연장 구간을
-- 벗어났다 다시 들어온 것이라 한 번 더 알려야 한다.
--
-- NULL 은 아직 알린 적이 없다는 뜻이다. 기존 행은 전부 그렇게 본다.
ALTER TABLE `auction_items`
    ADD COLUMN `notified_at` datetime(6) DEFAULT NULL;
