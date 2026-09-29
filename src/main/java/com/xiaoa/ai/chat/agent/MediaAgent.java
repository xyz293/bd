package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.dto.GenerateRequest;
import org.springframework.stereotype.Component;

/**
 * 媒体任务 Agent（mediaSubmit 节点，挂起点 hold3）：
 * 配图/视频提交 AI 网关异步任务（网关内部扣费 + 结算 + 失败退款），提交后挂起：
 * 快照槽位已落 gen_task（网关内），回调出片后作品入库，前端轮询作品状态。
 *
 * <p>隔离记忆：提交的作品 ID。</p>
 */
@Component
public class MediaAgent extends BaseNodeAgent {

    private final com.xiaoa.ai.service.AiGatewayService aiGatewayService;

    public MediaAgent(AgentMemoryService agentMemory, com.xiaoa.ai.service.AiGatewayService aiGatewayService) {
        super(agentMemory);
        this.aiGatewayService = aiGatewayService;
    }

    @Override
    public String name() {
        return "mediaAgent";
    }

    @Override
    public String systemPrompt() {
        return "媒体任务 Agent：配图/视频提交 AI 网关异步任务（网关内部扣费+失败退款），提交后挂起，前端轮询作品库。";
    }

    @Override
    public void invoke(ChatFlowState state) {
        com.fasterxml.jackson.databind.JsonNode context = parse(state.getContextJson());
        GenerateRequest request = new GenerateRequest();
        request.setType(ChatFlowState.TASK_VIDEO.equals(state.getTaskType()) ? "video" : "image");
        request.setPlatform(defaultIfBlank(textOf(context, "platform"),
                defaultIfBlank(state.getSession().getScene(), "朋友圈")));
        request.setProductName(defaultIfBlank(textOf(context, "product"), "未指定商品"));
        StringBuilder input = new StringBuilder(defaultIfBlank(state.getSession().getScene(), ""));
        if (!isBlank(state.getUserInput())) {
            if (input.length() > 0) {
                input.append(" ");
            }
            input.append(state.getUserInput());
        }
        request.setUserInput(input.toString());
        request.setChatSessionId(state.getSession().getId());
        com.xiaoa.ai.model.Work work = aiGatewayService.generate(request);
        state.setMediaWorkId(work.getId());
        state.setReplyAction(ChatFlowState.REPLY_PENDING_MEDIA);
        state.setQuestion((ChatFlowState.TASK_VIDEO.equals(state.getTaskType()) ? "视频" : "配图")
                + "任务已提交，正在生成中～完成后可在作品库查看，失败会自动退回额度。");
        remember(state, "workId", String.valueOf(work.getId()));
    }
}
