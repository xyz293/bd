-- 开发环境演示数据：所有写入均通过业务自然键判断，重复执行不会重复生成数据。

INSERT INTO tenant (name, type, industry, status, expire_at, deleted)
SELECT '演示美妆集团', 1, 'BEAUTY', 1, DATE_ADD(NOW(), INTERVAL 365 DAY), 0
WHERE NOT EXISTS (
    SELECT 1 FROM tenant WHERE name = '演示美妆集团' AND deleted = 0
);
SET @seed_tenant_id = (
    SELECT id FROM tenant WHERE name = '演示美妆集团' AND deleted = 0 ORDER BY id LIMIT 1
);

INSERT INTO asset_package (name, industry, version, status)
SELECT '美妆行业基础素材包', 'BEAUTY', 'v1.0', 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM asset_package WHERE industry = 'BEAUTY' AND version = 'v1.0'
);
SET @seed_asset_package_id = (
    SELECT id FROM asset_package WHERE industry = 'BEAUTY' AND version = 'v1.0' LIMIT 1
);
UPDATE tenant SET asset_package_id = @seed_asset_package_id
WHERE id = @seed_tenant_id AND (asset_package_id IS NULL OR asset_package_id = 0);

INSERT INTO org (tenant_id, parent_id, type, name, deleted)
SELECT @seed_tenant_id, 0, 1, '演示美妆集团总部', 0
WHERE NOT EXISTS (
    SELECT 1 FROM org WHERE tenant_id = @seed_tenant_id AND type = 1
      AND name = '演示美妆集团总部' AND deleted = 0
);
SET @seed_brand_id = (
    SELECT id FROM org WHERE tenant_id = @seed_tenant_id AND type = 1
      AND name = '演示美妆集团总部' AND deleted = 0 ORDER BY id LIMIT 1
);

INSERT INTO org (tenant_id, parent_id, type, name, deleted)
SELECT @seed_tenant_id, @seed_brand_id, 2, '华东区域', 0
WHERE NOT EXISTS (
    SELECT 1 FROM org WHERE tenant_id = @seed_tenant_id AND type = 2
      AND name = '华东区域' AND deleted = 0
);
SET @seed_region_id = (
    SELECT id FROM org WHERE tenant_id = @seed_tenant_id AND type = 2
      AND name = '华东区域' AND deleted = 0 ORDER BY id LIMIT 1
);

INSERT INTO org (tenant_id, parent_id, type, name, deleted)
SELECT @seed_tenant_id, @seed_region_id, 3, '上海徐汇体验店', 0
WHERE NOT EXISTS (
    SELECT 1 FROM org WHERE tenant_id = @seed_tenant_id AND type = 3
      AND name = '上海徐汇体验店' AND deleted = 0
);
SET @seed_store_sh_id = (
    SELECT id FROM org WHERE tenant_id = @seed_tenant_id AND type = 3
      AND name = '上海徐汇体验店' AND deleted = 0 ORDER BY id LIMIT 1
);

INSERT INTO org (tenant_id, parent_id, type, name, deleted)
SELECT @seed_tenant_id, @seed_region_id, 3, '杭州西湖体验店', 0
WHERE NOT EXISTS (
    SELECT 1 FROM org WHERE tenant_id = @seed_tenant_id AND type = 3
      AND name = '杭州西湖体验店' AND deleted = 0
);
SET @seed_store_hz_id = (
    SELECT id FROM org WHERE tenant_id = @seed_tenant_id AND type = 3
      AND name = '杭州西湖体验店' AND deleted = 0 ORDER BY id LIMIT 1
);

INSERT INTO `user` (phone, openid, nickname, status, deleted)
SELECT '13800000001', 'seed-openid-hq', '演示总部管理员', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE phone = '13800000001' AND deleted = 0);
INSERT INTO `user` (phone, openid, nickname, status, deleted)
SELECT '13800000002', 'seed-openid-region', '演示区域管理员', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE phone = '13800000002' AND deleted = 0);
INSERT INTO `user` (phone, openid, nickname, status, deleted)
SELECT '13800000003', 'seed-openid-owner-sh', '上海徐汇店店长', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE phone = '13800000003' AND deleted = 0);
INSERT INTO `user` (phone, openid, nickname, status, deleted)
SELECT '13800000004', 'seed-openid-staff-sh', '上海徐汇店员工', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE phone = '13800000004' AND deleted = 0);
INSERT INTO `user` (phone, openid, nickname, status, deleted)
SELECT '13800000005', 'seed-openid-owner-hz', '杭州西湖店店长', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE phone = '13800000005' AND deleted = 0);
INSERT INTO `user` (phone, openid, nickname, status, deleted)
SELECT '13800000006', 'seed-openid-staff-hz', '杭州西湖店员工', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE phone = '13800000006' AND deleted = 0);

SET @seed_hq_user_id = (SELECT id FROM `user` WHERE phone = '13800000001' AND deleted = 0 LIMIT 1);
SET @seed_region_user_id = (SELECT id FROM `user` WHERE phone = '13800000002' AND deleted = 0 LIMIT 1);
SET @seed_owner_sh_id = (SELECT id FROM `user` WHERE phone = '13800000003' AND deleted = 0 LIMIT 1);
SET @seed_staff_sh_id = (SELECT id FROM `user` WHERE phone = '13800000004' AND deleted = 0 LIMIT 1);
SET @seed_owner_hz_id = (SELECT id FROM `user` WHERE phone = '13800000005' AND deleted = 0 LIMIT 1);
SET @seed_staff_hz_id = (SELECT id FROM `user` WHERE phone = '13800000006' AND deleted = 0 LIMIT 1);

INSERT INTO user_wechat_bind (user_id, openid, action)
SELECT @seed_hq_user_id, 'seed-openid-hq', 1
WHERE NOT EXISTS (
    SELECT 1 FROM user_wechat_bind WHERE user_id = @seed_hq_user_id AND openid = 'seed-openid-hq'
);
INSERT INTO user_wechat_bind (user_id, openid, action)
SELECT @seed_staff_sh_id, 'seed-openid-staff-sh', 1
WHERE NOT EXISTS (
    SELECT 1 FROM user_wechat_bind WHERE user_id = @seed_staff_sh_id AND openid = 'seed-openid-staff-sh'
);

INSERT INTO user_org_role (tenant_id, user_id, org_id, role, data_scope, status)
SELECT @seed_tenant_id, @seed_hq_user_id, @seed_brand_id, 'HQ_ADMIN', 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM user_org_role WHERE user_id = @seed_hq_user_id
      AND org_id = @seed_brand_id AND role = 'HQ_ADMIN'
);
INSERT INTO user_org_role (tenant_id, user_id, org_id, role, data_scope, status)
SELECT @seed_tenant_id, @seed_region_user_id, @seed_region_id, 'REGION_ADMIN', 2, 1
WHERE NOT EXISTS (
    SELECT 1 FROM user_org_role WHERE user_id = @seed_region_user_id
      AND org_id = @seed_region_id AND role = 'REGION_ADMIN'
);
INSERT INTO user_org_role (tenant_id, user_id, org_id, role, data_scope, status)
SELECT @seed_tenant_id, @seed_owner_sh_id, @seed_store_sh_id, 'OWNER', 3, 1
WHERE NOT EXISTS (
    SELECT 1 FROM user_org_role WHERE user_id = @seed_owner_sh_id
      AND org_id = @seed_store_sh_id AND role = 'OWNER'
);
INSERT INTO user_org_role (tenant_id, user_id, org_id, role, data_scope, status)
SELECT @seed_tenant_id, @seed_staff_sh_id, @seed_store_sh_id, 'STAFF', 4, 1
WHERE NOT EXISTS (
    SELECT 1 FROM user_org_role WHERE user_id = @seed_staff_sh_id
      AND org_id = @seed_store_sh_id AND role = 'STAFF'
);
INSERT INTO user_org_role (tenant_id, user_id, org_id, role, data_scope, status)
SELECT @seed_tenant_id, @seed_owner_hz_id, @seed_store_hz_id, 'OWNER', 3, 1
WHERE NOT EXISTS (
    SELECT 1 FROM user_org_role WHERE user_id = @seed_owner_hz_id
      AND org_id = @seed_store_hz_id AND role = 'OWNER'
);
INSERT INTO user_org_role (tenant_id, user_id, org_id, role, data_scope, status)
SELECT @seed_tenant_id, @seed_staff_hz_id, @seed_store_hz_id, 'STAFF', 4, 1
WHERE NOT EXISTS (
    SELECT 1 FROM user_org_role WHERE user_id = @seed_staff_hz_id
      AND org_id = @seed_store_hz_id AND role = 'STAFF'
);

INSERT INTO invite_code (tenant_id, store_id, code, role, expire_at, used, created_by)
SELECT @seed_tenant_id, @seed_store_sh_id, 'SEED-SH-STAFF-001', 'STAFF', DATE_ADD(NOW(), INTERVAL 30 DAY), 0, @seed_owner_sh_id
WHERE NOT EXISTS (SELECT 1 FROM invite_code WHERE code = 'SEED-SH-STAFF-001');

INSERT INTO quota_account (tenant_id, level, owner_id, balance, version)
SELECT @seed_tenant_id, 'TENANT', @seed_tenant_id, 8000, 3
WHERE NOT EXISTS (
    SELECT 1 FROM quota_account WHERE tenant_id = @seed_tenant_id
      AND level = 'TENANT' AND owner_id = @seed_tenant_id
);
INSERT INTO quota_account (tenant_id, level, owner_id, balance, version)
SELECT @seed_tenant_id, 'STORE', @seed_store_sh_id, 1200, 1
WHERE NOT EXISTS (
    SELECT 1 FROM quota_account WHERE tenant_id = @seed_tenant_id
      AND level = 'STORE' AND owner_id = @seed_store_sh_id
);
INSERT INTO quota_account (tenant_id, level, owner_id, balance, version)
SELECT @seed_tenant_id, 'STORE', @seed_store_hz_id, 800, 1
WHERE NOT EXISTS (
    SELECT 1 FROM quota_account WHERE tenant_id = @seed_tenant_id
      AND level = 'STORE' AND owner_id = @seed_store_hz_id
);
SET @seed_pool_account_id = (
    SELECT id FROM quota_account WHERE tenant_id = @seed_tenant_id
      AND level = 'TENANT' AND owner_id = @seed_tenant_id LIMIT 1
);
SET @seed_quota_sh_id = (
    SELECT id FROM quota_account WHERE tenant_id = @seed_tenant_id
      AND level = 'STORE' AND owner_id = @seed_store_sh_id LIMIT 1
);
SET @seed_quota_hz_id = (
    SELECT id FROM quota_account WHERE tenant_id = @seed_tenant_id
      AND level = 'STORE' AND owner_id = @seed_store_hz_id LIMIT 1
);

INSERT INTO quota_flow (tenant_id, account_id, biz_type, biz_id, amount, balance_after, remark)
SELECT @seed_tenant_id, @seed_pool_account_id, 'CREDIT', 'SEED:CREDIT:TENANT', 10000, 10000, '开发演示初始额度'
WHERE NOT EXISTS (SELECT 1 FROM quota_flow WHERE account_id = @seed_pool_account_id AND biz_id = 'SEED:CREDIT:TENANT');
INSERT INTO quota_flow (tenant_id, account_id, biz_type, biz_id, amount, balance_after, remark)
SELECT @seed_tenant_id, @seed_pool_account_id, 'ALLOCATE', 'SEED:ALLOCATE:SH:POOL', -2000, 8000, '分配上海徐汇店演示额度'
WHERE NOT EXISTS (SELECT 1 FROM quota_flow WHERE account_id = @seed_pool_account_id AND biz_id = 'SEED:ALLOCATE:SH:POOL');
INSERT INTO quota_flow (tenant_id, account_id, biz_type, biz_id, amount, balance_after, remark)
SELECT @seed_tenant_id, @seed_quota_sh_id, 'ALLOCATE', 'SEED:ALLOCATE:SH:STORE', 1200, 1200, '上海徐汇店演示额度'
WHERE NOT EXISTS (SELECT 1 FROM quota_flow WHERE account_id = @seed_quota_sh_id AND biz_id = 'SEED:ALLOCATE:SH:STORE');
INSERT INTO quota_flow (tenant_id, account_id, biz_type, biz_id, amount, balance_after, remark)
SELECT @seed_tenant_id, @seed_quota_hz_id, 'ALLOCATE', 'SEED:ALLOCATE:HZ:STORE', 800, 800, '杭州西湖店演示额度'
WHERE NOT EXISTS (SELECT 1 FROM quota_flow WHERE account_id = @seed_quota_hz_id AND biz_id = 'SEED:ALLOCATE:HZ:STORE');

INSERT INTO content_package (tenant_id, name, calendar_date, publish_at, copy_direction, task_template, status, created_by)
SELECT @seed_tenant_id, '春季防晒新品推广包', DATE_ADD(CURDATE(), INTERVAL 3 DAY),
       DATE_ADD(NOW(), INTERVAL 3 DAY), '突出轻薄、防水和通勤场景',
       JSON_OBJECT('platform', 'DOUYIN', 'frequency', 1, 'targetScope', 1), 1, @seed_hq_user_id
WHERE NOT EXISTS (
    SELECT 1 FROM content_package WHERE tenant_id = @seed_tenant_id AND name = '春季防晒新品推广包'
);
SET @seed_package_id = (
    SELECT id FROM content_package WHERE tenant_id = @seed_tenant_id
      AND name = '春季防晒新品推广包' ORDER BY id LIMIT 1
);

INSERT INTO style_option (tenant_id, package_id, name, description, example_url, sort_no, status, version)
SELECT @seed_tenant_id, @seed_package_id, '清新通勤', '明亮、自然、适合日常门店营销', 'local://seed/style/commute.jpg', 10, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM style_option WHERE tenant_id = @seed_tenant_id AND name = '清新通勤'
);
INSERT INTO style_option (tenant_id, package_id, name, description, example_url, sort_no, status, version)
SELECT @seed_tenant_id, @seed_package_id, '高级质感', '低饱和、高级感、适合新品发布', 'local://seed/style/luxury.jpg', 20, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM style_option WHERE tenant_id = @seed_tenant_id AND name = '高级质感'
);
SET @seed_style_id = (
    SELECT id FROM style_option WHERE tenant_id = @seed_tenant_id
      AND name = '清新通勤' ORDER BY id LIMIT 1
);

INSERT INTO compliance_word (tenant_id, word, level, replacement, status, created_by)
SELECT @seed_tenant_id, '绝对第一', 1, '更具竞争力', 1, @seed_hq_user_id
WHERE NOT EXISTS (
    SELECT 1 FROM compliance_word WHERE tenant_id = @seed_tenant_id AND word = '绝对第一'
);
INSERT INTO compliance_word (tenant_id, word, level, replacement, status, created_by)
SELECT @seed_tenant_id, '医疗治愈', 2, NULL, 1, @seed_hq_user_id
WHERE NOT EXISTS (
    SELECT 1 FROM compliance_word WHERE tenant_id = @seed_tenant_id AND word = '医疗治愈'
);

INSERT INTO audit_config (tenant_id, org_id, enabled, updated_by)
SELECT @seed_tenant_id, 0, 1, @seed_hq_user_id
WHERE NOT EXISTS (
    SELECT 1 FROM audit_config WHERE tenant_id = @seed_tenant_id AND org_id = 0
);
INSERT INTO audit_config (tenant_id, org_id, enabled, updated_by)
SELECT @seed_tenant_id, @seed_region_id, 1, @seed_region_user_id
WHERE NOT EXISTS (
    SELECT 1 FROM audit_config WHERE tenant_id = @seed_tenant_id AND org_id = @seed_region_id
);

INSERT INTO asset (tenant_id, package_id, type, name, content, version, status, owner_type, owner_id, recommend_status)
SELECT NULL, @seed_asset_package_id, 'PRODUCT_IMAGE', '春季防晒霜主视觉', 'local://seed/assets/sunscreen-main.jpg', 'v1.0', 'ACTIVE', 2, NULL, 2
WHERE NOT EXISTS (
    SELECT 1 FROM asset WHERE package_id = @seed_asset_package_id AND name = '春季防晒霜主视觉'
);
INSERT INTO asset (tenant_id, package_id, type, name, content, version, status, owner_type, owner_id, recommend_status)
SELECT @seed_tenant_id, @seed_package_id, 'SCRIPT', '防晒通勤话术', '早八通勤、轻薄不黏，出门前完成日常防晒。', 'v1.0', 'ACTIVE', 1, @seed_tenant_id, 1
WHERE NOT EXISTS (
    SELECT 1 FROM asset WHERE tenant_id = @seed_tenant_id AND name = '防晒通勤话术'
);

INSERT INTO prompt_template (scene, template, version, status)
SELECT 'IMAGE', '请输出适合{{platform}}的{{style}}营销图文，产品是{{productName}}，用户要求：{{userInput}}。', 2, 0
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scene = 'IMAGE' AND version = 2);
INSERT INTO prompt_template (scene, template, version, status)
SELECT 'VIDEO', '请输出适合{{platform}}的{{style}}短视频脚本，产品是{{productName}}，用户要求：{{userInput}}。', 2, 0
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scene = 'VIDEO' AND version = 2);

INSERT INTO chat_session (tenant_id, user_id, title, status)
SELECT @seed_tenant_id, @seed_staff_sh_id, '防晒新品营销咨询', 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM chat_session WHERE tenant_id = @seed_tenant_id
      AND user_id = @seed_staff_sh_id AND title = '防晒新品营销咨询'
);
SET @seed_chat_session_id = (
    SELECT id FROM chat_session WHERE tenant_id = @seed_tenant_id
      AND user_id = @seed_staff_sh_id AND title = '防晒新品营销咨询' ORDER BY id LIMIT 1
);
INSERT INTO chat_message (tenant_id, session_id, user_id, role, content, token_count)
SELECT @seed_tenant_id, @seed_chat_session_id, @seed_staff_sh_id, 'user', '帮我写一条春季防晒新品的短文案', 18
WHERE NOT EXISTS (
    SELECT 1 FROM chat_message WHERE session_id = @seed_chat_session_id
      AND role = 'user' AND content = '帮我写一条春季防晒新品的短文案'
);
INSERT INTO chat_message (tenant_id, session_id, user_id, role, content, token_count)
SELECT @seed_tenant_id, @seed_chat_session_id, @seed_staff_sh_id, 'assistant', '轻薄不黏的春日防晒，通勤出门也要保持清透好状态。', 28
WHERE NOT EXISTS (
    SELECT 1 FROM chat_message WHERE session_id = @seed_chat_session_id
      AND role = 'assistant' AND content = '轻薄不黏的春日防晒，通勤出门也要保持清透好状态。'
);

INSERT INTO `task` (tenant_id, title, form_type, content_package_id, platform, frequency, target_scope,
                    target_ids, judge_type, source_task_id, created_by, created_level, status, start_at, end_at, deleted)
SELECT @seed_tenant_id, '每日发布门店新品内容', 1, NULL, 'DOUYIN', 1, 1,
       JSON_ARRAY(), 2, NULL, @seed_hq_user_id, 1, 1, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 0
WHERE NOT EXISTS (
    SELECT 1 FROM `task` WHERE tenant_id = @seed_tenant_id AND title = '每日发布门店新品内容' AND deleted = 0
);
SET @seed_global_task_id = (
    SELECT id FROM `task` WHERE tenant_id = @seed_tenant_id
      AND title = '每日发布门店新品内容' AND deleted = 0 ORDER BY id LIMIT 1
);

INSERT INTO `task` (tenant_id, title, form_type, content_package_id, platform, frequency, target_scope,
                    target_ids, judge_type, source_task_id, created_by, created_level, status, start_at, end_at, deleted)
SELECT @seed_tenant_id, '徐汇店周末防晒推广', 2, @seed_package_id, 'DOUYIN', 2, 3,
       JSON_ARRAY(@seed_store_sh_id), 1, NULL, @seed_owner_sh_id, 3, 1, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 0
WHERE NOT EXISTS (
    SELECT 1 FROM `task` WHERE tenant_id = @seed_tenant_id AND title = '徐汇店周末防晒推广' AND deleted = 0
);
SET @seed_store_task_id = (
    SELECT id FROM `task` WHERE tenant_id = @seed_tenant_id
      AND title = '徐汇店周末防晒推广' AND deleted = 0 ORDER BY id LIMIT 1
);

INSERT INTO task_modify_log (tenant_id, task_id, modified_by, change_detail)
SELECT @seed_tenant_id, @seed_store_task_id, @seed_owner_sh_id,
       JSON_OBJECT('before', JSON_OBJECT('title', '原始门店任务'),
                   'after', JSON_OBJECT('title', '徐汇店周末防晒推广', 'platform', 'DOUYIN'))
WHERE NOT EXISTS (
    SELECT 1 FROM task_modify_log WHERE tenant_id = @seed_tenant_id
      AND task_id = @seed_store_task_id AND modified_by = @seed_owner_sh_id
);

INSERT INTO task_record (tenant_id, task_id, user_id, store_id, period_date, status, publish_record_id)
SELECT @seed_tenant_id, @seed_global_task_id, r.user_id, r.org_id, CURDATE(), 0, NULL
FROM user_org_role r
WHERE r.tenant_id = @seed_tenant_id AND r.status = 1 AND r.role IN ('OWNER', 'STAFF')
  AND NOT EXISTS (
      SELECT 1 FROM task_record tr WHERE tr.task_id = @seed_global_task_id
        AND tr.user_id = r.user_id AND tr.period_date = CURDATE()
  );
INSERT INTO task_record (tenant_id, task_id, user_id, store_id, period_date, status, publish_record_id)
SELECT @seed_tenant_id, @seed_store_task_id, r.user_id, r.org_id,
       DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), 0, NULL
FROM user_org_role r
WHERE r.tenant_id = @seed_tenant_id AND r.status = 1 AND r.org_id = @seed_store_sh_id
  AND r.role IN ('OWNER', 'STAFF')
  AND NOT EXISTS (
      SELECT 1 FROM task_record tr WHERE tr.task_id = @seed_store_task_id
        AND tr.user_id = r.user_id
        AND tr.period_date = DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY)
  );

INSERT INTO `work` (tenant_id, user_id, type, media_task_id, platform, style_id, style_name, user_input,
                    ref_image_urls, prompt_template_id, prompt_template_version, content_url, copywriting, status, fail_reason)
SELECT @seed_tenant_id, @seed_staff_sh_id, 'IMAGE', NULL, 'DOUYIN', @seed_style_id, '清新通勤',
       '突出轻薄不黏和春季通勤场景', 'local://seed/assets/sunscreen-main.jpg',
       (SELECT id FROM prompt_template WHERE scene = 'IMAGE' AND status = 1 ORDER BY version DESC LIMIT 1),
       (SELECT version FROM prompt_template WHERE scene = 'IMAGE' AND status = 1 ORDER BY version DESC LIMIT 1),
       'local://seed/works/sunscreen-image.jpg', '春日通勤防晒，轻薄不黏，清透一整天。', 'SUCCESS', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `work` WHERE tenant_id = @seed_tenant_id AND user_id = @seed_staff_sh_id
      AND type = 'IMAGE' AND copywriting = '春日通勤防晒，轻薄不黏，清透一整天。'
);
SET @seed_image_work_id = (
    SELECT id FROM `work` WHERE tenant_id = @seed_tenant_id AND user_id = @seed_staff_sh_id
      AND type = 'IMAGE' AND copywriting = '春日通勤防晒，轻薄不黏，清透一整天。' ORDER BY id LIMIT 1
);

INSERT INTO `work` (tenant_id, user_id, type, media_task_id, platform, style_id, style_name, user_input,
                    ref_image_urls, prompt_template_id, prompt_template_version, content_url, copywriting, status, fail_reason)
SELECT @seed_tenant_id, @seed_staff_hz_id, 'VIDEO', NULL, 'DOUYIN', @seed_style_id, '清新通勤',
       '生成一条 15 秒防晒短视频脚本', 'local://seed/assets/sunscreen-main.jpg',
       (SELECT id FROM prompt_template WHERE scene = 'VIDEO' AND status = 1 ORDER BY version DESC LIMIT 1),
       (SELECT version FROM prompt_template WHERE scene = 'VIDEO' AND status = 1 ORDER BY version DESC LIMIT 1),
       NULL, NULL, 'FAILED', '演示任务：模型超时，已自动退款'
WHERE NOT EXISTS (
    SELECT 1 FROM `work` WHERE tenant_id = @seed_tenant_id AND user_id = @seed_staff_hz_id
      AND type = 'VIDEO' AND user_input = '生成一条 15 秒防晒短视频脚本'
);
SET @seed_video_work_id = (
    SELECT id FROM `work` WHERE tenant_id = @seed_tenant_id AND user_id = @seed_staff_hz_id
      AND type = 'VIDEO' AND user_input = '生成一条 15 秒防晒短视频脚本' ORDER BY id LIMIT 1
);

INSERT INTO media_task (tenant_id, user_id, work_id, store_id, type, scene, status, prompt,
                        provider_task_id, result_url, cost, error_message, refund_status, started_at, finished_at)
SELECT @seed_tenant_id, @seed_staff_sh_id, @seed_image_work_id, @seed_store_sh_id, 'IMAGE', 'IMAGE', 'SUCCESS',
       '你是门店营销内容专家，请为DOUYIN平台创作一条清新通勤风格的营销内容。产品：春季防晒霜。',
       NULL, 'local://seed/works/sunscreen-image.jpg', 1, NULL, 0, DATE_SUB(NOW(), INTERVAL 2 MINUTE), DATE_SUB(NOW(), INTERVAL 1 MINUTE)
WHERE NOT EXISTS (
    SELECT 1 FROM media_task WHERE work_id = @seed_image_work_id
);
SET @seed_image_media_task_id = (
    SELECT id FROM media_task WHERE work_id = @seed_image_work_id ORDER BY id LIMIT 1
);

INSERT INTO media_task (tenant_id, user_id, work_id, store_id, type, scene, status, prompt,
                        provider_task_id, result_url, cost, error_message, refund_status, started_at, finished_at)
SELECT @seed_tenant_id, @seed_staff_hz_id, @seed_video_work_id, @seed_store_hz_id, 'VIDEO', 'VIDEO', 'FAILED',
       '你是门店短视频创作专家，请为DOUYIN平台设计一条清新通勤风格的视频脚本。产品：春季防晒霜。',
       'seed-video-task-001', NULL, 5, 'MODEL_TIMEOUT：演示任务超时', 1, DATE_SUB(NOW(), INTERVAL 35 MINUTE), DATE_SUB(NOW(), INTERVAL 5 MINUTE)
WHERE NOT EXISTS (
    SELECT 1 FROM media_task WHERE work_id = @seed_video_work_id
);
SET @seed_video_media_task_id = (
    SELECT id FROM media_task WHERE work_id = @seed_video_work_id ORDER BY id LIMIT 1
);
UPDATE `work` SET media_task_id = @seed_image_media_task_id
WHERE id = @seed_image_work_id AND (media_task_id IS NULL OR media_task_id = 0);
UPDATE `work` SET media_task_id = @seed_video_media_task_id
WHERE id = @seed_video_work_id AND (media_task_id IS NULL OR media_task_id = 0);

INSERT INTO publish_record (tenant_id, work_id, user_id, task_id, platform, proof_url)
SELECT @seed_tenant_id, @seed_image_work_id, @seed_staff_sh_id, @seed_global_task_id,
       'DOUYIN', 'local://seed/proofs/sunscreen-publish.png'
WHERE NOT EXISTS (
    SELECT 1 FROM publish_record WHERE tenant_id = @seed_tenant_id
      AND work_id = @seed_image_work_id AND task_id = @seed_global_task_id
);
SET @seed_publish_id = (
    SELECT id FROM publish_record WHERE tenant_id = @seed_tenant_id
      AND work_id = @seed_image_work_id AND task_id = @seed_global_task_id ORDER BY id LIMIT 1
);
UPDATE task_record SET status = 1, publish_record_id = @seed_publish_id, finished_at = NOW()
WHERE task_id = @seed_global_task_id AND user_id = @seed_staff_sh_id AND period_date = CURDATE();

INSERT INTO audit_record (tenant_id, work_id, reviewer_id, status, opinion, reviewed_at)
SELECT @seed_tenant_id, @seed_image_work_id, @seed_hq_user_id, 'APPROVED', '演示作品审核通过', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM audit_record WHERE tenant_id = @seed_tenant_id AND work_id = @seed_image_work_id
);
INSERT INTO audit_record (tenant_id, work_id, reviewer_id, status, opinion)
SELECT @seed_tenant_id, @seed_video_work_id, NULL, 'PENDING', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM audit_record WHERE tenant_id = @seed_tenant_id AND work_id = @seed_video_work_id
);

INSERT INTO payment_order (tenant_id, order_no, amount, channel, status, callback_payload, paid_at)
SELECT @seed_tenant_id, 'SEED-PAY-202603-001', 19900, 'DEMO', 'PAID',
       JSON_OBJECT('source', 'seed', 'tradeNo', 'SEED-TRADE-001'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payment_order WHERE order_no = 'SEED-PAY-202603-001');

INSERT INTO track_log (tenant_id, user_id, event, props)
SELECT @seed_tenant_id, @seed_staff_sh_id, 'AI_GENERATE_SUCCESS',
       JSON_OBJECT('workId', @seed_image_work_id, 'type', 'IMAGE', 'source', 'seed')
WHERE NOT EXISTS (
    SELECT 1 FROM track_log WHERE tenant_id = @seed_tenant_id
      AND user_id = @seed_staff_sh_id AND event = 'AI_GENERATE_SUCCESS'
);

INSERT INTO message (tenant_id, user_id, type, title, content)
SELECT @seed_tenant_id, @seed_staff_sh_id, 'TASK_REMINDER', '任务提醒', '每日发布门店新品内容尚未完成，请及时处理。'
WHERE NOT EXISTS (
    SELECT 1 FROM message WHERE tenant_id = @seed_tenant_id AND user_id = @seed_staff_sh_id
      AND type = 'TASK_REMINDER' AND content = '每日发布门店新品内容尚未完成，请及时处理。'
);
INSERT INTO message (tenant_id, user_id, type, title, content, read_at)
SELECT @seed_tenant_id, @seed_staff_hz_id, 'AI_GENERATE_FAILED', '生成失败退款提醒', '视频生成失败，额度已自动退回。', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM message WHERE tenant_id = @seed_tenant_id AND user_id = @seed_staff_hz_id
      AND type = 'AI_GENERATE_FAILED'
);

INSERT INTO export_task (tenant_id, created_by, export_type, query_params, status, file_url, finished_at)
SELECT @seed_tenant_id, @seed_hq_user_id, 4, JSON_OBJECT('range', 'CURRENT_MONTH', 'source', 'seed'),
       1, 'local://export/seed/production.csv', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM export_task WHERE tenant_id = @seed_tenant_id
      AND created_by = @seed_hq_user_id AND export_type = 4
);
