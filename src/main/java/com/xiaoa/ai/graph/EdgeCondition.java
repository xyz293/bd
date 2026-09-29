package com.xiaoa.ai.graph;

/**
 * 条件边路由函数：根据当前共享状态返回分支名，配合
 * {@link StateGraph#addConditionalEdges} 的分支映射决定下一个节点。
 *
 * <p>对齐 LangGraph 的 conditional edge 语义（返回分支 key，查表路由）。</p>
 *
 * @param <S> 共享状态类型
 */
@FunctionalInterface
public interface EdgeCondition<S> {

    /**
     * @return 分支名，必须能在 addConditionalEdges 传入的映射中找到目标节点
     */
    String apply(S state);
}
