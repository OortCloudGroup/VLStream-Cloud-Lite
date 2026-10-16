-- Authenticated manual RTSP/ONVIF creation owns its tenant.
-- Assign only NULL/exact-empty legacy ownership to the approved configured default.
-- This candidate has not been released or applied to production. Existing nonempty owners are immutable.

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

-- Narrow scope approved by the user: RTSP/ONVIF only; never derive owner from the deploying login.
UPDATE rtsp_device SET tenant_id = '${protocol_legacy_tenant_id}'
WHERE tenant_id IS NULL OR OCTET_LENGTH(tenant_id) = 0;
UPDATE onvif_device SET tenant_id = '${protocol_legacy_tenant_id}'
WHERE tenant_id IS NULL OR OCTET_LENGTH(tenant_id) = 0;
