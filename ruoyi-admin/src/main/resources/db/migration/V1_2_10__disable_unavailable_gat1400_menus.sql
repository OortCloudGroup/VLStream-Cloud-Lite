-- GAT1400 pages have no corresponding backend implementation in Lite.
-- Disable the legacy entries without deleting menus, role grants or business data.
-- Match paths instead of menu IDs so existing installations with different IDs work.
UPDATE sys_menu
SET status = '1'
WHERE path IN ('gat1400', '/gat1400')
   OR component LIKE 'gat1400/%'
   OR component LIKE '/gat1400/%';
