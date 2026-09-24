-- 资产配置域升级：素材三层可见性（scope）+ 分类 + 上传人 + 状态语义迁移；内容包补下发回填字段。
-- asset.status 语义从 ACTIVE 迁移为 APPROVED/PENDING_REVIEW/REJECTED/DELETED（复用原列，仅换值与默认值）。
-- asset.scope：PLATFORM(行业包)/BRAND(品牌)/STORE(本店)；STORE 层素材用 store_id 定位。

SET @asset_scope_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset' AND column_name = 'scope'
);
SET @asset_scope_sql = IF(@asset_scope_exists = 0,
    'ALTER TABLE asset ADD COLUMN scope VARCHAR(10) NOT NULL DEFAULT ''STORE'' COMMENT ''PLATFORM(行业包)/BRAND(品牌)/STORE(本店)''',
    'SELECT 1');
PREPARE asset_scope_stmt FROM @asset_scope_sql;
EXECUTE asset_scope_stmt;
DEALLOCATE PREPARE asset_scope_stmt;

SET @asset_store_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset' AND column_name = 'store_id'
);
SET @asset_store_sql = IF(@asset_store_exists = 0,
    'ALTER TABLE asset ADD COLUMN store_id BIGINT NULL COMMENT ''STORE 层素材所属门店''',
    'SELECT 1');
PREPARE asset_store_stmt FROM @asset_store_sql;
EXECUTE asset_store_stmt;
DEALLOCATE PREPARE asset_store_stmt;

SET @asset_category_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset' AND column_name = 'category'
);
SET @asset_category_sql = IF(@asset_category_exists = 0,
    'ALTER TABLE asset ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT ''DEFAULT'' COMMENT ''分类''',
    'SELECT 1');
PREPARE asset_category_stmt FROM @asset_category_sql;
EXECUTE asset_category_stmt;
DEALLOCATE PREPARE asset_category_stmt;

SET @asset_uploader_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'asset' AND column_name = 'uploader_id'
);
SET @asset_uploader_sql = IF(@asset_uploader_exists = 0,
    'ALTER TABLE asset ADD COLUMN uploader_id BIGINT NULL COMMENT ''上传人''',
    'SELECT 1');
PREPARE asset_uploader_stmt FROM @asset_uploader_sql;
EXECUTE asset_uploader_stmt;
DEALLOCATE PREPARE asset_uploader_stmt;

-- 历史数据 scope 回填：平台行业包素材（无租户归属）→ PLATFORM；租户自有素材 → BRAND。
-- 注：V5 种子的租户素材误带了 package_id（指向 content_package），按租户归属优先判为 BRAND。
UPDATE asset SET scope = 'PLATFORM' WHERE tenant_id IS NULL AND scope = 'STORE';
UPDATE asset SET scope = 'BRAND' WHERE tenant_id IS NOT NULL AND scope = 'STORE';
-- 修正误判：有租户归属的 PLATFORM 行回正为 BRAND（合法行业包行 tenant_id 恒为空，不受影响）。
UPDATE asset SET scope = 'BRAND' WHERE tenant_id IS NOT NULL AND scope = 'PLATFORM';

-- 状态语义迁移：ACTIVE → APPROVED；默认值同步改为 APPROVED。
UPDATE asset SET status = 'APPROVED' WHERE status = 'ACTIVE';
ALTER TABLE asset ALTER COLUMN status SET DEFAULT 'APPROVED';

SET @asset_vis_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'asset' AND index_name = 'idx_vis'
);
SET @asset_vis_index_sql = IF(@asset_vis_index_exists = 0,
    'ALTER TABLE asset ADD INDEX idx_vis (tenant_id, scope, status, category)',
    'SELECT 1');
PREPARE asset_vis_index_stmt FROM @asset_vis_index_sql;
EXECUTE asset_vis_index_stmt;
DEALLOCATE PREPARE asset_vis_index_stmt;

-- content_package（V3 已建表）：补下发回填与失败记录字段。
SET @cp_source_task_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'content_package' AND column_name = 'source_task_id'
);
SET @cp_source_task_sql = IF(@cp_source_task_exists = 0,
    'ALTER TABLE content_package ADD COLUMN source_task_id BIGINT NULL COMMENT ''下发后回填的任务ID''',
    'SELECT 1');
PREPARE cp_source_task_stmt FROM @cp_source_task_sql;
EXECUTE cp_source_task_stmt;
DEALLOCATE PREPARE cp_source_task_stmt;

SET @cp_last_error_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'content_package' AND column_name = 'last_error'
);
SET @cp_last_error_sql = IF(@cp_last_error_exists = 0,
    'ALTER TABLE content_package ADD COLUMN last_error VARCHAR(512) NULL COMMENT ''下发失败原因，待人工补建''',
    'SELECT 1');
PREPARE cp_last_error_stmt FROM @cp_last_error_sql;
EXECUTE cp_last_error_stmt;
DEALLOCATE PREPARE cp_last_error_stmt;
