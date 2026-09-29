package com.xiaoa.ai.chat.job;

import com.xiaoa.ai.chat.dto.PendingOption;
import com.xiaoa.ai.chat.service.ChatFlowService;
import com.xiaoa.ai.chat.service.ChatMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 选项卡挂起兜底任务（方案 ⑤ 超时策略）：
 * 前端 30s 计时，员工不选/关闭页面时由本任务扫描挂起超过 60s 的会话，
 * 按「直接生成」（默认 D）兜底放行，保证预扣的额度一定有产出、会话一定有结果。
 *
 * <p>与员工主动选择（/chat/option）通过 Redis 挂起键的抢占式删除竞争，
 * 不会重复生成。</p>
 */
@Component
public class ChatPendingSweeper {

    private static final Logger log = LoggerFactory.getLogger(ChatPendingSweeper.class);

    /** 兜底宽限：挂起超过该毫秒数仍未恢复 → 默认放行 */
    private static final long GRACE_MILLIS = 60_000L;

    private final ChatMemoryService memoryService;
    private final ChatFlowService flowService;

    public ChatPendingSweeper(ChatMemoryService memoryService, ChatFlowService flowService) {
        this.memoryService = memoryService;
        this.flowService = flowService;
    }

    /** 每 15s 扫描一次（可配置 xiaoa.chat.option-sweeper-ms）。 */
    @Scheduled(fixedDelayString = "${xiaoa.chat.option-sweeper-ms:15000}")
    public void sweepExpiredOptionCards() {
        List<PendingOption> expired = memoryService.scanExpiredOptions(GRACE_MILLIS);
        for (PendingOption pending : expired) {
            try {
                flowService.releaseOptionTimeout(pending);
            } catch (Exception exception) {
                // 单个会话放行失败不影响其它会话，下一轮重试
                log.warn("选项卡超时兜底放行失败 sessionId={}", pending.getSessionId(), exception);
            }
        }
    }
}
