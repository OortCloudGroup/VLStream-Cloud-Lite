-- Legacy devices without a reported tenant continue to belong to the configured default tenant.
-- Do not guess/backfill a tenant ID from an operator's current login.
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `wvp_vlstream_device` ADD COLUMN `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL', 'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'wvp_vlstream_device' AND COLUMN_NAME = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'CREATE INDEX `idx_vlstream_device_tenant` ON `wvp_vlstream_device` (`tenant_id`)', 'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'wvp_vlstream_device' AND INDEX_NAME = 'idx_vlstream_device_tenant');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
