CREATE INDEX idx_dc_auction_status_rank
    ON deal_candidates (auction_item_id, status, candidate_rank);
