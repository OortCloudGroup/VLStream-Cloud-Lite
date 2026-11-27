ALTER TABLE wvp_vlstream_device
    ADD COLUMN claimed_at datetime DEFAULT NULL,
    ADD COLUMN claimed_by bigint DEFAULT NULL;
