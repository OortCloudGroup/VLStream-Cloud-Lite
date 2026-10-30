-- Revocable, source-scoped delegation for VLS background detection. Never store a browser token.
CREATE TABLE IF NOT EXISTS wvp_cloud_ai_source_grant (
    id varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tenant_id varchar(64) COLLATE utf8mb4_bin NOT NULL,
    actor_id varchar(128) COLLATE utf8mb4_bin NOT NULL,
    protocol varchar(16) NOT NULL,
    device_row_id bigint NOT NULL,
    stream_id bigint NULL,
    department_id bigint NULL,
    source_identity varchar(128) COLLATE utf8mb4_bin NOT NULL,
    revoked tinyint NOT NULL DEFAULT 0,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_time datetime NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cloud_ai_grant_token (token_hash),
    KEY idx_cloud_ai_grant_owner (tenant_id, actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云端检测后台取流授权';
