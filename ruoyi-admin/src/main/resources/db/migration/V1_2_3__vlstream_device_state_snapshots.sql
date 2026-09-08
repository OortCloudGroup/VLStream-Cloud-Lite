-- Device-reported state snapshots. Historical online times remain unknown.

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `wvp_vlstream_device` ADD COLUMN `last_online_time` datetime NULL', 'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'wvp_vlstream_device' AND COLUMN_NAME = 'last_online_time');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `wvp_vlstream_device` ADD COLUMN `capabilities_json` longtext NULL', 'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'wvp_vlstream_device' AND COLUMN_NAME = 'capabilities_json');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `wvp_vlstream_device` ADD COLUMN `models_json` longtext NULL', 'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'wvp_vlstream_device' AND COLUMN_NAME = 'models_json');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
