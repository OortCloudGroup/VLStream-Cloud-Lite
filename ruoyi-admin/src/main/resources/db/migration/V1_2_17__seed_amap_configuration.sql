-- Map credentials are configured per installation through system parameters.
-- Use generated IDs because older installations may already use IDs 12 and 13.
INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '高德地图 JS API Key', 'amap.key', '', 'Y', 'system', NOW(),
       '填写当前部署使用的高德地图 Web 端 JS API Key，与 amap.security.key 配套使用'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'amap.key'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '高德地图安全密钥', 'amap.security.key', '', 'Y', 'system', NOW(),
       '填写与 amap.key 配套的高德地图安全密钥 securityJsCode'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'amap.security.key'
);
