package com.xiaoa.ai.graph;

import org.springframework.lang.NonNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LangGraph 风格的状态图定义（对齐 LangGraph 核心 API：
 * {@code addNode / addEdge / addConditionalEdges / compile / invoke}，以及 {@link #START} / {@link #END}）。
 *
 * <p>与 LangGraph 一致，图以「共享状态」为核心：节点是 {@code state -> state} 的函数，
 * 边描述状态在节点间的流转；条件边根据状态在运行期选择分支，天然支持追问/出稿这类分支循环编排。</p>
 *
 * <p>编译期做完整性校验（入口必填、节点不重复、所有出边目标必须存在），
 * 运行期带最大步数保护，防止配置出无限环路。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * CompiledGraph<MyState> graph = new StateGraph<MyState>()
 *         .addNode("prepare", this::prepare)
 *         .addNode("ask", this::ask)
 *         .addNode("generate", this::generate)
 *         .setEntryPoint("prepare")
 *         .addEdge("prepare", "route")
 *         .addConditionalEdges("route", state -> state.isReady() ? "go" : "more",
 *                 branches("go", "generate", "more", "ask"))
 *         .addEdge("ask", StateGraph.END)
 *         .addEdge("generate", StateGraph.END)
 *         .compile();
 * MyState result = graph.invoke(new MyState());
 * }</pre>
 *
 * @param <S> 共享状态类型
 */
public class StateGraph<S> {

    /** 虚拟起点节点。 */
    public static final String START = "__START__";
    /** 虚拟终点节点，走到 END 本次 invoke 结束。 */
    public static final String END = "__END__";

    private final Map<String, NodeFunction<S>> nodes = new LinkedHashMap<String, NodeFunction<S>>();
    private final Map<String, String> edges = new HashMap<String, String>();
    private final Map<String, EdgeCondition<S>> conditions = new HashMap<String, EdgeCondition<S>>();
    private final Map<String, Map<String, String>> conditionMappings = new HashMap<String, Map<String, String>>();
    private String entryPoint;

    /** 注册节点，节点名不可重复。 */
    public StateGraph<S> addNode(@NonNull String name, @NonNull NodeFunction<S> node) {
        if (nodes.containsKey(name)) {
            throw new IllegalStateException("节点重复注册: " + name);
        }
        nodes.put(name, node);
        return this;
    }

    /** 固定边：from 执行完固定流转到 to（to 可为 {@link #END}）。 */
    public StateGraph<S> addEdge(@NonNull String from, @NonNull String to) {
        edges.put(from, to);
        return this;
    }

    /**
     * 条件边：from 执行完调用 condition 得到分支名，查 mapping 得到目标节点。
     *
     * @param mapping 分支名 -&gt; 目标节点（目标可为 {@link #END}）
     */
    public StateGraph<S> addConditionalEdges(@NonNull String from, @NonNull EdgeCondition<S> condition,
                                             @NonNull Map<String, String> mapping) {
        conditions.put(from, condition);
        conditionMappings.put(from, new HashMap<String, String>(mapping));
        return this;
    }

    /** 设置入口节点；也可以不调用，改为从 START 连出边。 */
    public StateGraph<S> setEntryPoint(@NonNull String name) {
        this.entryPoint = name;
        return this;
    }

    /** 校验图定义并编译为可执行图。 */
    public CompiledGraph<S> compile() {
        String entry = entryPoint != null ? entryPoint
                : (edges.containsKey(START) || conditions.containsKey(START) ? START : null);
        if (entry == null) {
            throw new IllegalStateException("状态图缺少入口：请调用 setEntryPoint 或从 START 连出边");
        }
        if (!nodes.containsKey(entry) && !START.equals(entry)) {
            throw new IllegalStateException("入口节点未注册: " + entry);
        }
        for (String name : nodes.keySet()) {
            if (!edges.containsKey(name) && !conditions.containsKey(name)) {
                throw new IllegalStateException("节点缺少出边: " + name);
            }
        }
        validateTargets(edges);
        for (Map<String, String> mapping : conditionMappings.values()) {
            validateTargets(mapping);
        }
        return new CompiledGraph<S>(entry, nodes, edges, conditions, conditionMappings);
    }

    private void validateTargets(Map<String, String> targets) {
        for (String target : targets.values()) {
            if (!nodes.containsKey(target) && !END.equals(target) && !START.equals(target)) {
                throw new IllegalStateException("边目标未注册: " + target);
            }
        }
    }
}
