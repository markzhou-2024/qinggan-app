ALTER TABLE trip ADD COLUMN actual_start_date DATE NULL;
ALTER TABLE trip ADD COLUMN time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai';
ALTER TABLE navigation_point ADD COLUMN amap_poi_id VARCHAR(128) NULL;

UPDATE trip SET status = 'PLANNING' WHERE status = 'ACTIVE';
