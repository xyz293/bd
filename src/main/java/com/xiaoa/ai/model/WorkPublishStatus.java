package com.xiaoa.ai.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 作品发布维度状态机。生成维度（PENDING/SUCCESS/FAILED）不变，
 * 本状态仅在生成成功（SUCCESS）后按审核开关初始化并单向流转。
 */
public final class WorkPublishStatus {

    /** 初始态：生成中/生成失败/历史数据未初始化。 */
    public static final String NONE = "NONE";
    /** 审核开关关闭时的默认态，可直接发布。 */
    public static final String DRAFT = "DRAFT";
    /** 审核开关开启时进入待审。 */
    public static final String PENDING_AUDIT = "PENDING_AUDIT";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String PUBLISHED = "PUBLISHED";

    private static final Map<String, Set<String>> ALLOWED = buildAllowed();

    private WorkPublishStatus() {
    }

    public static boolean canTransit(String from, String to) {
        Set<String> targets = ALLOWED.get(from);
        return targets != null && targets.contains(to);
    }

    private static Map<String, Set<String>> buildAllowed() {
        Map<String, Set<String>> map = new HashMap<>();
        map.put(NONE, setOf(DRAFT, PENDING_AUDIT));
        map.put(DRAFT, setOf(PUBLISHED));
        map.put(PENDING_AUDIT, setOf(APPROVED, REJECTED));
        map.put(APPROVED, setOf(PUBLISHED));
        map.put(REJECTED, setOf(PENDING_AUDIT));
        return Collections.unmodifiableMap(map);
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(values)));
    }
}
