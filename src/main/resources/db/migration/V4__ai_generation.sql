CREATE TABLE IF NOT EXISTS prompt_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scene VARCHAR(32) NOT NULL COMMENT 'IMAGE/VIDEO',
    template TEXT NOT NULL COMMENT '模板，{{变量}}占位',
    version INT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_prompt_template_scene_status (scene, status, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `work`
    ADD COLUMN platform VARCHAR(32) NULL,
    ADD COLUMN style_id BIGINT NULL,
    ADD COLUMN style_name VARCHAR(64) NULL,
    ADD COLUMN user_input TEXT NULL,
    ADD COLUMN ref_image_urls TEXT NULL,
    ADD COLUMN prompt_template_id BIGINT NULL,
    ADD COLUMN prompt_template_version INT NULL,
    ADD COLUMN content_url VARCHAR(1024) NULL,
    ADD COLUMN fail_reason VARCHAR(512) NULL;

ALTER TABLE media_task
    ADD COLUMN work_id BIGINT NULL,
    ADD COLUMN store_id BIGINT NULL,
    ADD COLUMN scene VARCHAR(16) NULL,
    ADD COLUMN refund_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未退款 1已退款';

ALTER TABLE media_task
    ADD KEY idx_media_task_work (tenant_id, work_id),
    ADD KEY idx_media_task_poll (type, status, updated_at),
    ADD KEY idx_media_task_refund (status, refund_status, updated_at);

INSERT INTO prompt_template (scene, template, version, status)
SELECT 'IMAGE', '你是门店营销内容专家，请为{{platform}}平台创作一条{{style}}风格的营销内容。产品：{{productName}}。要求：{{userInput}}。参考话术：{{scriptRef}}。', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scene = 'IMAGE' AND status = 1);

INSERT INTO prompt_template (scene, template, version, status)
SELECT 'VIDEO', '你是门店短视频创作专家，请为{{platform}}平台设计一条{{style}}风格的视频脚本。产品：{{productName}}。要求：{{userInput}}。参考话术：{{scriptRef}}。', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scene = 'VIDEO' AND status = 1);
