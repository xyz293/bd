package com.xiaoa.ai.chat.provider;

/**
 * 文案大模型适配器（与媒体生成的 AiProvider 同构）。
 * 文案生成是同步调用（几秒内返回），不走任务表。
 */
public interface LlmProvider {

    LlmResponse complete(LlmRequest request);
}
