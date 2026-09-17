-- EHome legacy devices are deliberately separate from ISUP 5 devices.
CREATE TABLE IF NOT EXISTS ehome_device (
    id bigint NOT NULL AUTO_INCREMENT,
    dept_id bigint DEFAULT NULL,
    device_id varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    name varchar(64) DEFAULT NULL,
    device_serial varchar(128) DEFAULT NULL,
    ip_address varchar(128) DEFAULT NULL,
    firmware_version varchar(64) DEFAULT NULL,
    dev_protocol_version varchar(16) DEFAULT NULL,
    luser_id int DEFAULT NULL,
    status varchar(16) NOT NULL DEFAULT 'OFFLINE',
    remark varchar(500) DEFAULT NULL,
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_time datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_ehome_device_id (device_id), KEY idx_ehome_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EHome 2.x/3.x/4.x devices';

-- Allocate IDs from the existing menu sequence; no assumptions about custom installations.
SET @ehome_root = (SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='ehome' LIMIT 1);
SET @ehome_root = COALESCE(@ehome_root, (SELECT COALESCE(MAX(menu_id),0)+1 FROM sys_menu));
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @ehome_root,'EHome协议',0,7,'ehome',NULL,1,0,'M','0','0','','ehome','admin',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=@ehome_root);
SET @ehome_page = (SELECT menu_id FROM sys_menu WHERE parent_id=@ehome_root AND path='device' LIMIT 1);
SET @ehome_page = COALESCE(@ehome_page, (SELECT COALESCE(MAX(menu_id),0)+1 FROM sys_menu));
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @ehome_page,'设备管理',@ehome_root,1,'device','ehome/device/index','EhomeDevice',1,1,'C','0','0','ehome:device:list','ehome','admin',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=@ehome_page);
SET @ehome_action = (SELECT MAX(menu_id)+1 FROM sys_menu);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
VALUES
(@ehome_action,'EHome通道查询',@ehome_page,1,'#',1,0,'F','0','0','ehome:device:query','#','admin',NOW()),
(@ehome_action+1,'EHome实时预览',@ehome_page,2,'#',1,0,'F','0','0','ehome:device:preview','#','admin',NOW()),
(@ehome_action+2,'EHome设备编辑',@ehome_page,3,'#',1,0,'F','0','0','ehome:device:edit','#','admin',NOW());
-- New protocol access is assigned to the administrator; other roles can be granted explicitly.
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m
ON (m.menu_id=@ehome_root OR m.menu_id=@ehome_page OR m.parent_id=@ehome_page)
WHERE r.role_key='admin';
