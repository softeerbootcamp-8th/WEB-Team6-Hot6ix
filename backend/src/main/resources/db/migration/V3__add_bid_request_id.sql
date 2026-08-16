ALTER TABLE bids
    ADD COLUMN request_id VARCHAR(64) NULL AFTER bid_id,
    ADD CONSTRAINT uk_bids_request_id UNIQUE (request_id);
