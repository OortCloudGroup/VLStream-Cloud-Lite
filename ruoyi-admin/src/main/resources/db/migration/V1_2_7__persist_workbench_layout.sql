CREATE TABLE IF NOT EXISTS sys_work_layout (
    id BIGINT NOT NULL,
    layout_list LONGTEXT NULL,
    layout_index VARCHAR(64) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作台共享布局，沿用原全局布局语义';
