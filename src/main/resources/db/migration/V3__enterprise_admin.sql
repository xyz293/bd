CREATE TABLE IF NOT EXISTS content_package (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NULL,
    name VARCHAR(128) NOT NULL,
    calendar_date DATE NULL,
    publish_at DATETIME NULL,
    copy_direction TEXT NULL,
    task_template JSON NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_content_package_tenant_date (tenant_id, calendar_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS style_option (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NULL,
    package_id BIGINT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    example_url VARCHAR(1024) NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_style_tenant_status (tenant_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS compliance_word (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    word VARCHAR(128) NOT NULL,
    level TINYINT NOT NULL COMMENT '1提示 2拦截',
    replacement VARCHAR(128) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_compliance_tenant_word (tenant_id, word),
    KEY idx_compliance_tenant_status (tenant_id, status, level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL DEFAULT 0 COMMENT '0品牌级配置',
    enabled TINYINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_audit_config_tenant_org (tenant_id, org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS export_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    export_type TINYINT NOT NULL COMMENT '1消耗 2充值 3任务 4产出',
    query_params JSON NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0处理中 1完成 2失败',
    file_url VARCHAR(512) NULL,
    fail_reason VARCHAR(255) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME NULL,
    KEY idx_export_tenant_status (tenant_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @asset_owner_type_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset' AND column_name = 'owner_type'
);
SET @asset_owner_type_sql = IF(@asset_owner_type_exists = 0,
    'ALTER TABLE asset ADD COLUMN owner_type TINYINT NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE asset_owner_type_stmt FROM @asset_owner_type_sql;
EXECUTE asset_owner_type_stmt;
DEALLOCATE PREPARE asset_owner_type_stmt;

SET @asset_owner_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset' AND column_name = 'owner_id'
);
SET @asset_owner_id_sql = IF(@asset_owner_id_exists = 0,
    'ALTER TABLE asset ADD COLUMN owner_id BIGINT NULL', 'SELECT 1');
PREPARE asset_owner_id_stmt FROM @asset_owner_id_sql;
EXECUTE asset_owner_id_stmt;
DEALLOCATE PREPARE asset_owner_id_stmt;

SET @asset_recommend_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset' AND column_name = 'recommend_status'
);
SET @asset_recommend_sql = IF(@asset_recommend_exists = 0,
    'ALTER TABLE asset ADD COLUMN recommend_status TINYINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE asset_recommend_stmt FROM @asset_recommend_sql;
EXECUTE asset_recommend_stmt;
DEALLOCATE PREPARE asset_recommend_stmt;

SET @quota_biz_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'quota_flow' AND index_name = 'uk_quota_flow_biz'
);
SET @quota_biz_index_sql = IF(@quota_biz_index_exists > 0,
    'ALTER TABLE quota_flow DROP INDEX uk_quota_flow_biz', 'SELECT 1');
PREPARE quota_biz_index_stmt FROM @quota_biz_index_sql;
EXECUTE quota_biz_index_stmt;
DEALLOCATE PREPARE quota_biz_index_stmt;

SET @quota_account_biz_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'quota_flow' AND index_name = 'uk_quota_flow_account_biz'
);
SET @quota_account_biz_index_sql = IF(@quota_account_biz_index_exists = 0,
    'ALTER TABLE quota_flow ADD UNIQUE KEY uk_quota_flow_account_biz (account_id, biz_id)', 'SELECT 1');
PREPARE quota_account_biz_index_stmt FROM @quota_account_biz_index_sql;
EXECUTE quota_account_biz_index_stmt;
DEALLOCATE PREPARE quota_account_biz_index_stmt;
