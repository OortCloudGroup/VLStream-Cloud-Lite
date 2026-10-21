-- Authenticated manual RTSP/ONVIF creation owns its tenant.
-- Schema only: preserve every existing owner and legacy NULL/exact-empty value.
-- Approved legacy data assignment is performed separately after this migration succeeds.
-- This candidate has not been released or applied to production.

SET @ddl = (SELECT IF(COUNT(*)=0,
    'ALTER TABLE `rtsp_device` ADD COLUMN `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL', 'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='rtsp_device' AND COLUMN_NAME='tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*)=0,
    'CREATE INDEX `idx_rtsp_device_tenant` ON `rtsp_device` (`tenant_id`,`id`)', 'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='rtsp_device' AND INDEX_NAME='idx_rtsp_device_tenant');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*)=0,
    'ALTER TABLE `onvif_device` ADD COLUMN `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL', 'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='onvif_device' AND COLUMN_NAME='tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*)=0,
    'CREATE INDEX `idx_onvif_device_tenant` ON `onvif_device` (`tenant_id`,`id`)', 'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='onvif_device' AND INDEX_NAME='idx_onvif_device_tenant');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
