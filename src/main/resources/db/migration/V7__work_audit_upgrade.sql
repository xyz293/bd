-- 作品发布维度状态机 + 审核联动 + 图文成套 + 素材推优。
-- publish_status 状态机（单向流转，非法流转拒绝）：
--   NONE -> DRAFT / PENDING_AUDIT（生成成功时按审核开关初始化）
--   DRAFT -> PUBLISHED
--   PENDING_AUDIT -> APPROVED / REJECTED
--   APPROVED -> PUBLISHED
--   REJECTED -> PENDING_AUDIT（改稿后自动重提审）

SET @work_publish_status_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'work' AND column_name = 'publish_status'
);
SET @work_publish_status_sql = IF(@work_publish_status_exists = 0,
    'ALTER TABLE `work` ADD COLUMN publish_status VARCHAR(20) NOT NULL DEFAULT ''NONE'' COMMENT ''NONE/DRAFT/PENDING_AUDIT/APPROVED/REJECTED/PUBLISHED''',
    'SELECT 1');
PREPARE work_publish_status_stmt FROM @work_publish_status_sql;
EXECUTE work_publish_status_stmt;
DEALLOCATE PREPARE work_publish_status_stmt;

SET @work_caption_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'work' AND column_name = 'caption'
);
SET @work_caption_sql = IF(@work_caption_exists = 0,
    'ALTER TABLE `work` ADD COLUMN caption TEXT NULL COMMENT ''配套文案（图文成套）''',
    'SELECT 1');
PREPARE work_caption_stmt FROM @work_caption_sql;
EXECUTE work_caption_stmt;
DEALLOCATE PREPARE work_caption_stmt;

SET @work_source_assets_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'work' AND column_name = 'source_asset_ids'
);
SET @work_source_assets_sql = IF(@work_source_assets_exists = 0,
    'ALTER TABLE `work` ADD COLUMN source_asset_ids JSON NULL COMMENT ''引用素材快照''',
    'SELECT 1');
PREPARE work_source_assets_stmt FROM @work_source_assets_sql;
EXECUTE work_source_assets_stmt;
DEALLOCATE PREPARE work_source_assets_stmt;

SET @work_ps_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'work' AND index_name = 'idx_ps'
);
SET @work_ps_index_sql = IF(@work_ps_index_exists = 0,
    'ALTER TABLE `work` ADD INDEX idx_ps (tenant_id, publish_status)',
    'SELECT 1');
PREPARE work_ps_index_stmt FROM @work_ps_index_sql;
EXECUTE work_ps_index_stmt;
DEALLOCATE PREPARE work_ps_index_stmt;

-- 历史成功作品按"审核开关关闭"语义回填为 DRAFT，保证仍可发布；
-- 生成中/失败作品保持 NONE，等生成成功后按当时开关初始化。
UPDATE `work` SET publish_status = 'DRAFT'
WHERE status = 'SUCCESS' AND publish_status = 'NONE';

-- audit_record 复用 V1 表：reviewer_id=审核人、status 存 PASS/REJECT、opinion=审核意见，无需变更。
