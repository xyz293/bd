package com.xiaoa.ai.graph;

import org.springframework.lang.NonNull;

import java.util.Map;

/**
 * 编译后的可执行状态图：{@link #invoke} 从入口出发，依次执行节点并沿边/条件边流转，
 * 直到走到 {@link StateGraph#END} 或步数超限。
 *
 * <p>异常语义：任一节点抛出的 RuntimeException 直接向调用方传播，
 * 调用方可用事务回滚整条链路已产生的副作用。</p>
 *
 * @param <S> 共享状态类型
 */
public class CompiledGraph<S> {

    private static final int MAX_STEPS = 64;

    private final String entry;
    private final Map<String, NodeFunction<S>> nodes;
    private final Map<String, String> edges;
    private final Map<String, EdgeCondition<S>> conditions;
    private final Map<String, Map<String, String>> conditionMappings;

    CompiledGraph(String entry, Map<String, NodeFunction<S>> nodes, Map<String, String> edges,
                  Map<String, EdgeCondition<S>> conditions, Map<String, Map<String, String>> conditionMappings) {
        this.entry = entry;
        this.nodes = nodes;
        this.edges = edges;
        this.conditions = conditions;
        this.conditionMappings = conditionMappings;
    }

    /** 执行整张图，返回最终状态（与入参同一实例）。 */
    public @NonNull S invoke(@NonNull S state) {
        return invoke(state, null);
    }

    /**
     * 执行整张图，每执行完一个节点回调监听器（对齐 LangGraph stream 模式），返回最终状态。
     *
     * @param listener 节点监听器，可为 null（等价于 {@link #invoke}）
     */
    public @NonNull S invoke(@NonNull S state, NodeListener<S> listener) {
        String current = entry;
        int steps = 0;
        while (!StateGraph.END.equals(current)) {
            if (++steps > MAX_STEPS) {
                throw new IllegalStateException("状态图执行超过最大步数，疑似环路失控");
            }
            NodeFunction<S> node = nodes.get(current);
            if (node != null) {
                node.apply(state);
                if (listener != null) {
                    listener.afterNode(current, state);
                }
            } else if (!StateGraph.START.equals(current)) {
                throw new IllegalStateException("节点未注册: " + current);
            }
            current = nextOf(current, state);
        }
        return state;
    }

    private String nextOf(String from, S state) {
        EdgeCondition<S> condition = conditions.get(from);
        if (condition != null) {
            String branch = condition.apply(state);
            Map<String, String> mapping = conditionMappings.get(from);
            String target = mapping == null ? null : mapping.get(branch);
            if (target == null) {
                throw new IllegalStateException("条件边分支未映射: " + branch + " (from=" + from + ")");
            }
            return target;
        }
        String target = edges.get(from);
        if (target == null) {
            throw new IllegalStateException("节点缺少出边: " + from);
        }
        return target;
    }
}
