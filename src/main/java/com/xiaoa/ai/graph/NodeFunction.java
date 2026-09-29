package com.xiaoa.ai.graph;

/**
 * 图节点函数：读取共享状态、执行业务逻辑并回写状态。
 *
 * <p>对齐 LangGraph 的节点语义。本工程采用「可变共享状态」的简化实现：
 * 节点直接修改传入的 state，无需返回新实例（等价于 LangGraph 中节点返回全量新状态）。</p>
 *
 * @param <S> 共享状态类型
 */
@FunctionalInterface
public interface NodeFunction<S> {

    /**
     * 执行节点逻辑。抛出的任何 RuntimeException 会中断整条链路并向上传播
     * （对应 LangGraph 中节点异常导致本次 run 失败）。
     */
    void apply(S state);
}
