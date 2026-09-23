CREATE TABLE IF NOT EXISTS `work` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    media_task_id BIGINT NULL,
    copywriting TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_work_user (tenant_id, user_id, created_at),
    KEY idx_work_media_task (media_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(1024) NOT NULL,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_message_user_read (tenant_id, user_id, read_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS publish_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    work_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    platform VARCHAR(32) NOT NULL,
    proof_url VARCHAR(1024) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_publish_work (tenant_id, work_id),
    KEY idx_publish_task (tenant_id, task_id, user_id),
    KEY idx_publish_user (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @task_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'publish_record' AND column_name = 'task_id'
);
SET @task_id_alter = IF(@task_id_exists = 0,
    'ALTER TABLE publish_record ADD COLUMN task_id BIGINT NULL',
    'SELECT 1');
PREPARE task_id_stmt FROM @task_id_alter;
EXECUTE task_id_stmt;
DEALLOCATE PREPARE task_id_stmt;

CREATE TABLE IF NOT EXISTS `task` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    form_type TINYINT NOT NULL,
    content_package_id BIGINT NULL,
    platform VARCHAR(32) NULL,
    frequency TINYINT NOT NULL,
    target_scope TINYINT NOT NULL,
    target_ids JSON NULL,
    judge_type TINYINT NOT NULL DEFAULT 1,
    source_task_id BIGINT NULL,
    created_by BIGINT NOT NULL,
    created_level TINYINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    start_at DATETIME NULL,
    end_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    KEY idx_task_tenant_status (tenant_id, status, start_at, end_at),
    KEY idx_task_source (tenant_id, source_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS task_modify_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    modified_by BIGINT NOT NULL,
    change_detail JSON NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_task_modify_log_task (tenant_id, task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS task_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    period_date DATE NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    publish_record_id BIGINT NULL,
    finished_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_user_period (task_id, user_id, period_date),
    KEY idx_task_record_store_period (tenant_id, store_id, period_date, status),
    KEY idx_task_record_user_period (tenant_id, user_id, period_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
