package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.ChatFlowState;

/**
 * 节点 Agent 契约（每个图节点 = 一个 Agent）。
 *
 * <p>每个 Agent 有自己的名字（即隔离记忆的命名空间）与业务提示词（职责边界），
 * 只拥有当前节点业务逻辑所需的记忆；Agent 之间不互读记忆，
 * 跨 Agent 通信只走共享的 {@link ChatFlowState}（LangGraph State 语义）。</p>
 */
public interface NodeAgent {

    /** Agent 唯一名（同时是隔离记忆的命名空间，如 "gateAgent"）。 */
    String name();

    /** 本 Agent 的业务提示词（职责边界说明，供观测与复用）。 */
    String systemPrompt();

    /** 节点执行逻辑。 */
    void invoke(ChatFlowState state);
}
