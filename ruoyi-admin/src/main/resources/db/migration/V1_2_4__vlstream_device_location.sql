-- WGS84 decimal degrees; no fabricated coordinates for historical devices.
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `wvp_vlstream_device` ADD COLUMN `longitude` decimal(11,8) NULL', 'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'wvp_vlstream_device' AND COLUMN_NAME = 'longitude');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `wvp_vlstream_device` ADD COLUMN `latitude` decimal(10,8) NULL', 'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'wvp_vlstream_device' AND COLUMN_NAME = 'latitude');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
