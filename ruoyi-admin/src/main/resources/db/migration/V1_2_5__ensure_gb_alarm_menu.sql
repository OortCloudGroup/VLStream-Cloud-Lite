-- The alarm page and its WVP API already exist. Ensure upgraded databases expose
-- the page together with the rest of the GB28181 protocol entries.
INSERT INTO sys_menu (
  menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon,
  create_by, create_time, update_by, update_time, remark
) VALUES (
  2109, '报警管理', 2015, 9, 'alarm', 'wvp/alarm/index', NULL, 'Alarm',
  1, 0, 'C', '0', '0', 'wvp:alarm:list', 'color',
  'admin', NOW(), 'admin', NOW(), '国标报警管理'
)
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  parent_id = VALUES(parent_id),
  order_num = VALUES(order_num),
  path = VALUES(path),
  component = VALUES(component),
  route_name = VALUES(route_name),
  visible = VALUES(visible),
  status = VALUES(status),
  perms = VALUES(perms),
  icon = VALUES(icon),
  update_by = VALUES(update_by),
  update_time = VALUES(update_time),
  remark = VALUES(remark);
