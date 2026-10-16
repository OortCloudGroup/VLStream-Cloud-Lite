-- Default scene tags shared by the existing WVP classification catalogue.
-- Fresh installs and upgrades use this same Flyway migration. No device assignments,
-- tenant ownership, user-defined category fields or existing migration are changed.
-- Some older installations did not run the legacy classification table script.
CREATE TABLE IF NOT EXISTS `wvp_device_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `category_type` varchar(16) NOT NULL COMMENT 'REGION/GROUP/TAG',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父分类ID，0为根节点',
  `ancestors` varchar(1000) NOT NULL DEFAULT '0' COMMENT '祖级列表',
  `category_name` varchar(100) NOT NULL COMMENT '分类名称',
  `sort_num` int NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_sibling_name` (`category_type`,`parent_id`,`category_name`),
  KEY `idx_category_parent` (`category_type`,`parent_id`,`sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WVP设备全局分类';

CREATE TABLE IF NOT EXISTS `wvp_device_category_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `category_type` varchar(16) NOT NULL COMMENT 'REGION/GROUP/TAG',
  `protocol_type` varchar(16) NOT NULL COMMENT 'ISUP/RTSP/ONVIF/GB28181/DAHUA/VLSTREAM',
  `device_key` varchar(128) NOT NULL COMMENT '协议设备表主键，按字符串保存',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_category` (`protocol_type`,`device_key`,`category_id`),
  KEY `idx_category_protocol` (`category_id`,`protocol_type`),
  KEY `idx_device_category_type` (`protocol_type`,`device_key`,`category_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WVP设备分类关系';

-- Match the existing sibling-name rule: default parent groups are root TAGs.
-- Preserve same-named categories in other branches without using them as defaults.
INSERT INTO wvp_device_category
    (category_type, parent_id, ancestors, category_name, sort_num, create_by, update_by)
SELECT 'TAG', 0, '0', seed.category_name, seed.sort_num, 'system', 'system'
FROM (
    SELECT '交通类' AS category_name, 10 AS sort_num
    UNION ALL
    SELECT '自然类', 20
    UNION ALL
    SELECT '乡村类', 30
    UNION ALL
    SELECT '餐饮零售类', 40
    UNION ALL
    SELECT '市政设施类', 50
    UNION ALL
    SELECT '公园广场类', 60
    UNION ALL
    SELECT '工程作业类', 70
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM wvp_device_category existing
    WHERE existing.category_type = 'TAG' AND existing.parent_id = 0
      AND existing.category_name = seed.category_name
)
ORDER BY seed.sort_num;

-- Resolve each group by its root position and name, not by IDs in other branches.
-- Reuse a leaf only when it already exists under this exact default parent.
INSERT INTO wvp_device_category
    (category_type, parent_id, ancestors, category_name, sort_num, create_by, update_by)
SELECT 'TAG', parent.id, CONCAT(parent.ancestors, ',', parent.id),
       seed.category_name, seed.sort_num, 'system', 'system'
FROM (
    SELECT '交通类' AS parent_name, '交通道路' AS category_name, 10 AS sort_num
    UNION ALL
    SELECT '交通类', '交通要道', 20
    UNION ALL
    SELECT '交通类', '交通站点', 30
    UNION ALL
    SELECT '交通类', '地铁站', 40
    UNION ALL
    SELECT '交通类', '飞机场', 50
    UNION ALL
    SELECT '交通类', '火车站', 60
    UNION ALL
    SELECT '交通类', '汽车站', 70
    UNION ALL
    SELECT '交通类', '码头', 80
    UNION ALL
    SELECT '交通类', '桥隧', 90
    UNION ALL
    SELECT '交通类', '街角', 100
    UNION ALL
    SELECT '交通类', '十字路口', 110
    UNION ALL
    SELECT '交通类', '人行道', 120
    UNION ALL
    SELECT '交通类', '人行横道', 130
    UNION ALL
    SELECT '交通类', '安检区', 140
    UNION ALL
    SELECT '交通类', '候车大厅', 150
    UNION ALL
    SELECT '交通类', '高速收费站', 160
    UNION ALL
    SELECT '交通类', '卡口', 170
    UNION ALL
    SELECT '交通类', '车辆出入口', 180
    UNION ALL
    SELECT '交通类', '人员出入口', 190
    UNION ALL
    SELECT '交通类', '公交站点', 200
    UNION ALL
    SELECT '自然类', '大型水面', 10
    UNION ALL
    SELECT '自然类', '江河', 20
    UNION ALL
    SELECT '自然类', '山丘', 30
    UNION ALL
    SELECT '自然类', '森林', 40
    UNION ALL
    SELECT '自然类', '山林', 50
    UNION ALL
    SELECT '自然类', '野外灌木丛', 60
    UNION ALL
    SELECT '自然类', '田野', 70
    UNION ALL
    SELECT '乡村类', '村庄', 10
    UNION ALL
    SELECT '餐饮零售类', '露天买卖市场', 10
    UNION ALL
    SELECT '餐饮零售类', '餐饮场所', 20
    UNION ALL
    SELECT '餐饮零售类', '室内商场', 30
    UNION ALL
    SELECT '餐饮零售类', '商业门店', 40
    UNION ALL
    SELECT '市政设施类', '垃圾和垃圾箱', 10
    UNION ALL
    SELECT '市政设施类', '加油站', 20
    UNION ALL
    SELECT '市政设施类', '消防设施', 30
    UNION ALL
    SELECT '市政设施类', '垂直电梯', 40
    UNION ALL
    SELECT '公园广场类', '公园', 10
    UNION ALL
    SELECT '公园广场类', '露天活动广场', 20
    UNION ALL
    SELECT '工程作业类', '施工现场', 10
) seed
JOIN wvp_device_category parent
    ON parent.category_type = 'TAG' AND parent.parent_id = 0
    AND parent.category_name = seed.parent_name
WHERE NOT EXISTS (
    SELECT 1 FROM wvp_device_category existing
    WHERE existing.category_type = 'TAG' AND existing.parent_id = parent.id
      AND existing.category_name = seed.category_name
)
ORDER BY parent.sort_num, parent.id, seed.sort_num;

