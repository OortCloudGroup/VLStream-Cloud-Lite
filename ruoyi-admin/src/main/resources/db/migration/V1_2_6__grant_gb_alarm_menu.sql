-- Keep alarm visibility consistent with the parent GB28181 protocol menu.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 2109
FROM sys_role_menu
WHERE menu_id = 2015;
