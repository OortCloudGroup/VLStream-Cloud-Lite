-- 国标 SIP 服务身份（编号/域）支持在管理界面维护。
-- 表内无数据时继续使用 application.yml / 环境变量，避免升级时覆盖现场已配置的身份。
CREATE TABLE IF NOT EXISTS `wvp_sip_config` (
  `config_key` varchar(32) NOT NULL,
  `sip_id` varchar(20) NOT NULL,
  `sip_domain` varchar(10) NOT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='国标 SIP 服务身份配置';
