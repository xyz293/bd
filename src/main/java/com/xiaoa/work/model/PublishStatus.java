package com.xiaoa.work.model;

/**
 * 作品发布维度状态机。
 *
 * 单向流转：生成成功后按审核开关初始化为 DRAFT 或 PENDING_AUDIT；
 * DRAFT → PUBLISHED ｜ PENDING_AUDIT → APPROVED → PUBLISHED ｜ PENDING_AUDIT → REJECTED →（改稿）→ PENDING_AUDIT。
 * 生成维度（status: PENDING/SUCCESS/FAILED）与本状态互不影响。
 */
public enum PublishStatus {

    /** 生成中/生成失败，尚未进入发布维度 */
    NONE,
    DRAFT,
    PENDING_AUDIT,
    APPROVED,
    REJECTED,
    PUBLISHED;

    /**
     * 宽松解析数据库中的状态值，非法/空值回退为 NONE。
     */
    public static PublishStatus of(String value) {
        if (value == null || value.trim().isEmpty()) {
            return NONE;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }

    /**
     * 允许员工改稿的状态：草稿、已过审、被驳回（驳回态改稿后自动重提审）。
     */
    public static boolean editableForCaption(String publishStatus) {
        PublishStatus status = of(publishStatus);
        return status == DRAFT || status == APPROVED || status == REJECTED;
    }

    /**
     * 允许重新生成媒体的状态（同改稿，PENDING_AUDIT 中不可变更内容）。
     */
    public static boolean editableForRegenerate(String publishStatus) {
        PublishStatus status = of(publishStatus);
        return status == DRAFT || status == APPROVED || status == REJECTED;
    }

    /**
     * 允许发布的状态：草稿直发、过审发布；PUBLISHED 允许重复上报同一作品（多条发布记录）。
     */
    public static boolean publishable(String publishStatus) {
        PublishStatus status = of(publishStatus);
        return status == DRAFT || status == APPROVED || status == PUBLISHED;
    }
}
