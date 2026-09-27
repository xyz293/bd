-- 对话模式：会话要素上下文 + 作品关联会话。chat_message.role 列 V4 已有，无需变更。
ALTER TABLE chat_session ADD COLUMN scene VARCHAR(32) NULL COMMENT '创作场景（朋友圈/小红书/视频号）';
ALTER TABLE chat_session ADD COLUMN context JSON NULL COMMENT '要素收集状态（product/sellingPoint/audience等）';
ALTER TABLE chat_session ADD COLUMN revise_count INT NOT NULL DEFAULT 0 COMMENT '微调次数（计费递增键）';
ALTER TABLE work ADD COLUMN chat_session_id BIGINT NULL COMMENT '关联对话会话（去配图溯源）';
