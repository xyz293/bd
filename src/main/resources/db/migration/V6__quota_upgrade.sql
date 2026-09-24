-- 额度账务升级：幂等流水、支付收款单和对账告警。
-- V1 已创建 version/balance_after，本迁移只补充缺失字段和约束。

SET @quota_idem_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'quota_flow' AND column_name = 'idempotent_key'
);
SET @quota_idem_sql = IF(@quota_idem_exists = 0,
    'ALTER TABLE quota_flow ADD COLUMN idempotent_key VARCHAR(64) NULL COMMENT ''全局幂等键''',
    'SELECT 1');
PREPARE quota_idem_stmt FROM @quota_idem_sql;
EXECUTE quota_idem_stmt;
DEALLOCATE PREPARE quota_idem_stmt;

UPDATE quota_flow
SET idempotent_key = LEFT(CONCAT('legacy:', id), 64)
WHERE idempotent_key IS NULL OR idempotent_key = '';

SET @quota_idem_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'quota_flow' AND index_name = 'uk_quota_flow_idempotent_key'
);
SET @quota_idem_index_sql = IF(@quota_idem_index_exists = 0,
    'ALTER TABLE quota_flow ADD UNIQUE KEY uk_quota_flow_idempotent_key (idempotent_key)',
    'SELECT 1');
PREPARE quota_idem_index_stmt FROM @quota_idem_index_sql;
EXECUTE quota_idem_index_stmt;
DEALLOCATE PREPARE quota_idem_index_stmt;

SET @quota_idem_not_null_sql = 'ALTER TABLE quota_flow MODIFY COLUMN idempotent_key VARCHAR(64) NOT NULL COMMENT ''全局幂等键''';
PREPARE quota_idem_not_null_stmt FROM @quota_idem_not_null_sql;
EXECUTE quota_idem_not_null_stmt;
DEALLOCATE PREPARE quota_idem_not_null_stmt;

SET @payment_voucher_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'payment_order' AND column_name = 'voucher_url'
);
SET @payment_voucher_sql = IF(@payment_voucher_exists = 0,
    'ALTER TABLE payment_order ADD COLUMN voucher_url VARCHAR(1024) NULL COMMENT ''收款凭证''',
    'SELECT 1');
PREPARE payment_voucher_stmt FROM @payment_voucher_sql;
EXECUTE payment_voucher_stmt;
DEALLOCATE PREPARE payment_voucher_stmt;

SET @payment_invoice_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'payment_order' AND column_name = 'invoice_no'
);
SET @payment_invoice_sql = IF(@payment_invoice_exists = 0,
    'ALTER TABLE payment_order ADD COLUMN invoice_no VARCHAR(64) NULL COMMENT ''发票号''',
    'SELECT 1');
PREPARE payment_invoice_stmt FROM @payment_invoice_sql;
EXECUTE payment_invoice_stmt;
DEALLOCATE PREPARE payment_invoice_stmt;

SET @payment_confirm_by_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'payment_order' AND column_name = 'confirm_by'
);
SET @payment_confirm_by_sql = IF(@payment_confirm_by_exists = 0,
    'ALTER TABLE payment_order ADD COLUMN confirm_by BIGINT NULL COMMENT ''复核人''',
    'SELECT 1');
PREPARE payment_confirm_by_stmt FROM @payment_confirm_by_sql;
EXECUTE payment_confirm_by_stmt;
DEALLOCATE PREPARE payment_confirm_by_stmt;

SET @payment_confirmed_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'payment_order' AND column_name = 'confirmed_at'
);
SET @payment_confirmed_at_sql = IF(@payment_confirmed_at_exists = 0,
    'ALTER TABLE payment_order ADD COLUMN confirmed_at DATETIME NULL COMMENT ''复核时间''',
    'SELECT 1');
PREPARE payment_confirmed_at_stmt FROM @payment_confirmed_at_sql;
EXECUTE payment_confirmed_at_stmt;
DEALLOCATE PREPARE payment_confirmed_at_stmt;

UPDATE quota_flow SET biz_type = IF(amount < 0, 'ALLOCATE_OUT', 'ALLOCATE_IN')
WHERE biz_type = 'ALLOCATE';

UPDATE payment_order SET status = 'SETTLED'
WHERE status = 'PAID';

CREATE TABLE IF NOT EXISTS platform_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    login_name VARCHAR(64) NOT NULL,
    credential_hash VARCHAR(128) NOT NULL,
    nickname VARCHAR(64) NULL,
    role VARCHAR(32) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_user_login (login_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO platform_user (login_name, credential_hash, nickname, role, status)
SELECT 'platform-ops', SHA2('platform-ops-dev', 256), '平台运营', 'PLATFORM_OPS', 1
WHERE NOT EXISTS (SELECT 1 FROM platform_user WHERE login_name = 'platform-ops');
INSERT INTO platform_user (login_name, credential_hash, nickname, role, status)
SELECT 'platform-finance', SHA2('platform-finance-dev', 256), '平台财务', 'PLATFORM_FINANCE', 1
WHERE NOT EXISTS (SELECT 1 FROM platform_user WHERE login_name = 'platform-finance');

CREATE TABLE IF NOT EXISTS quota_reconcile_alert (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NULL,
    check_type VARCHAR(64) NOT NULL,
    expected_value VARCHAR(128) NULL,
    actual_value VARCHAR(128) NULL,
    detail VARCHAR(512) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0未处理 1已确认',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_quota_reconcile_alert_status (status, created_at),
    KEY idx_quota_reconcile_alert_tenant (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
