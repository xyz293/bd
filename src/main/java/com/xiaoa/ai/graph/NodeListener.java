package com.xiaoa.ai.graph;

/**
 * 节点监听器：每个节点执行完成后回调（对齐 LangGraph stream 模式的按节点输出语义）。
 *
 * <p>典型用途：SSE 阶段进度推送（intent_router → slot_extractor → … → response_composer），
 * 让前端实时展示图执行到哪个节点。</p>
 *
 * @param <S> 共享状态类型
 */
@FunctionalInterface
public interface NodeListener<S> {

    /**
     * 节点执行完成后的回调。
     *
     * @param nodeName 节点注册名（如 intentRouter / slotExtractor）
     * @param state    该节点执行后的共享状态（只读建议，修改会影响后续节点）
     */
    void afterNode(@org.springframework.lang.NonNull String nodeName, @org.springframework.lang.NonNull S state);
}
