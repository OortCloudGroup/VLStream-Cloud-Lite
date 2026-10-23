-- Tenant scoped read-only sharing for VLStream devices.
-- Existing devices remain private by default. No grants or role assignments are seeded.
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `wvp_vlstream_device` ADD COLUMN `visibility` varchar(16) NOT NULL DEFAULT ''PRIVATE''', 'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'wvp_vlstream_device' AND COLUMN_NAME = 'visibility');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `wvp_vlstream_access_request` (
  `request_id` bigint NOT NULL AUTO_INCREMENT,
  `device_row_id` bigint NOT NULL,
  `requester_tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `requester_user_id` bigint NOT NULL,
  `request_reason` varchar(1000) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `resolved_grant_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `decided_at` datetime DEFAULT NULL,
  `decided_by_tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `decided_by_user_id` bigint DEFAULT NULL,
  `decision_reason` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`request_id`),
  KEY `idx_vlstream_request_device_status` (`device_row_id`,`status`,`created_at`),
  KEY `idx_vlstream_request_grant` (`resolved_grant_id`),
  KEY `idx_vlstream_requester` (`requester_tenant_id`,`requester_user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VLStream tenant access requests';

CREATE TABLE IF NOT EXISTS `wvp_vlstream_access_grant` (
  `grant_id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` bigint NOT NULL,
  `device_row_id` bigint NOT NULL,
  `owner_tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `viewer_tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `approved_by_user_id` bigint NOT NULL,
  `expires_at` datetime NOT NULL,
  `expired_at` datetime DEFAULT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`grant_id`),
  UNIQUE KEY `uk_vlstream_grant_request` (`request_id`),
  KEY `idx_vlstream_grant_device_viewer_expiry` (`device_row_id`,`viewer_tenant_id`,`expires_at`,`revoked_at`),
  KEY `idx_vlstream_grant_expiration` (`expires_at`,`expired_at`,`revoked_at`),
  KEY `idx_vlstream_grant_owner` (`owner_tenant_id`,`device_row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Explicit read-only VLStream grants';

CREATE TABLE IF NOT EXISTS `wvp_vlstream_access_audit` (
  `audit_id` bigint NOT NULL AUTO_INCREMENT,
  `device_row_id` bigint NOT NULL,
  `actor_tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `actor_user_id` bigint NOT NULL,
  `action` varchar(32) NOT NULL,
  `request_id` bigint DEFAULT NULL,
  `grant_id` bigint DEFAULT NULL,
  `detail` varchar(1000) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`audit_id`),
  KEY `idx_vlstream_audit_device_time` (`device_row_id`,`created_at`),
  KEY `idx_vlstream_audit_actor` (`actor_tenant_id`,`actor_user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VLStream sharing audit log';

CREATE TABLE IF NOT EXISTS `wvp_vlstream_device_access_ref` (
  `device_row_id` bigint NOT NULL,
  `access_ref` char(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`device_row_id`),
  UNIQUE KEY `uk_vlstream_access_ref` (`access_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Opaque VLStream reference for cross-tenant share APIs';

-- Opaque references are not permissions; they are only IDs accepted by sharing endpoints.
INSERT IGNORE INTO `wvp_vlstream_device_access_ref` (`device_row_id`,`access_ref`)
SELECT `id`, REPLACE(UUID(),'-','') FROM `wvp_vlstream_device`;

CREATE TABLE IF NOT EXISTS `wvp_vlstream_preview_lease` (
  `lease_id` varchar(32) NOT NULL,
  `device_row_id` bigint NOT NULL,
  `stream_row_id` bigint NOT NULL,
  `viewer_tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `viewer_user_id` bigint NOT NULL,
  `grant_id` bigint DEFAULT NULL,
  `media_server_id` varchar(64) NOT NULL,
  `proxy_app` varchar(64) NOT NULL,
  `proxy_stream` varchar(128) NOT NULL,
  `proxy_key` varchar(128) NOT NULL,
  `source_fingerprint` char(64) NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `expires_at` datetime NOT NULL,
  `proxy_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`lease_id`),
  KEY `idx_vlstream_lease_device_state` (`device_row_id`,`state`,`expires_at`),
  KEY `idx_vlstream_lease_viewer` (`viewer_tenant_id`,`viewer_user_id`,`state`,`expires_at`),
  KEY `idx_vlstream_lease_cleanup` (`state`,`proxy_deleted`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-viewer VLStream preview leases';

CREATE TABLE IF NOT EXISTS `wvp_vlstream_camera_rtc_ticket` (
  `ticket_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `device_row_id` bigint NOT NULL,
  `stream_row_id` bigint NOT NULL,
  `viewer_tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `viewer_user_id` bigint NOT NULL,
  `grant_id` bigint DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `consumed_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`ticket_hash`),
  KEY `idx_vlstream_rtc_ticket_expiry` (`expires_at`,`consumed_at`),
  KEY `idx_vlstream_rtc_ticket_viewer` (`viewer_tenant_id`,`viewer_user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One-time tickets for authorized CameraRTC signaling relay';

-- Register dedicated permissions only. Existing roles receive no assignments from this migration.
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`)
SELECT COALESCE((SELECT MAX(menu_id)+1 FROM sys_menu),1),'视频共享设置',2301,20,'#','',NULL,'',1,0,'F','0','0','vlstream:device:share:manage','#','admin',NOW(),'admin',NOW(),'专用权限；仅设备所属租户可修改'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perms`='vlstream:device:share:manage');
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`)
SELECT COALESCE((SELECT MAX(menu_id)+1 FROM sys_menu),1),'视频共享审批',2301,21,'#','',NULL,'',1,0,'F','0','0','vlstream:device:share:approve','#','admin',NOW(),'admin',NOW(),'专用权限；并校验设备所属租户'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perms`='vlstream:device:share:approve');
