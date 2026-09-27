package com.xiaoa.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.admin.service.ComplianceService;
import com.xiaoa.ai.chat.mapper.ChatMessageMapper;
import com.xiaoa.ai.chat.mapper.ChatSessionMapper;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.model.ChatSession;
import com.xiaoa.ai.chat.dto.ChatReplyVO;
import com.xiaoa.ai.chat.dto.ChatReviseRequest;
import com.xiaoa.ai.chat.dto.ChatSendRequest;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话编排核心：存消息 → 组装 LLM 请求（系统要素 + 最近10轮历史 + 本轮输入）→
 * 结构化返回（ASK 追问不扣费 / GENERATE 合规检查后扣费出稿）→ 合并要素上下文。
 * 出稿扣费失败整体回滚（AI 消息不落，员工重发）。
 */
@Service
public class ChatFlowService {

    /** 携带给 LLM 的最近消息条数（10 轮 = 20 条） */
    private static final int HISTORY_MESSAGES = 20;
    private static final String FALLBACK_QUESTION = "能再具体一点吗？";

    private final ChatSessionService sessionService;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatBillingService billingService;
    private final ComplianceService complianceService;
    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatFlowService(ChatSessionService sessionService, ChatSessionMapper sessionMapper,
                           ChatMessageMapper messageMapper, ChatBillingService billingService,
                           ComplianceService complianceService, LlmProvider llmProvider) {
        this.sessionService = sessionService;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.billingService = billingService;
        this.complianceService = complianceService;
        this.llmProvider = llmProvider;
    }

    @Transactional
    public ChatReplyVO chat(Long sessionId, ChatSendRequest request) {
        AuthPrincipal principal = AuthContext.required();
        ChatSession session = sessionService.requireUsable(principal, sessionId);
        Long tenantId = principal.getTenantId();

        // 1. 存用户消息（任何后续失败随事务回滚，员工可重发）
        ChatMessage userMessage = message(tenantId, session, ChatMessage.ROLE_USER, request.getText());
        messageMapper.insert(userMessage);

        // 2. 组装 LLM 请求：要素上下文 + 最近N轮历史 + 本轮输入
        String contextJson = session.getContext() == null ? "{}" : session.getContext();
        List<ChatMessage> recent = messageMapper.findRecent(session.getId(), HISTORY_MESSAGES);
        Collections.reverse(recent);

        // 3. 调 LLM（非法返回重试由 Provider 内处理；仍失败兜底追问）
        LlmResponse response;
        try {
            response = llmProvider.complete(LlmRequest.chat(session.getScene(), contextJson,
                    historyJson(recent), request.getText()));
        } catch (RuntimeException exception) {
            response = LlmResponse.ask(FALLBACK_QUESTION, null);
        }

        // 4. 分支处理
        String mergedContext = mergeContext(contextJson, response.getContextPatchJson());
        boolean generate = LlmResponse.ACTION_GENERATE.equals(response.getAction())
                && response.getVersions() != null && !response.getVersions().isEmpty();
        List<String> versions = new ArrayList<String>();
        if (generate) {
            for (String version : response.getVersions()) {
                // 合规：level1 替换 / level2 拦截（拦截抛 4001，整体回滚）
                versions.add(complianceService.filterText(tenantId, version));
            }
            boolean allBlank = true;
            for (String version : versions) {
                if (version != null && !version.trim().isEmpty()) {
                    allBlank = false;
                    break;
                }
            }
            generate = !allBlank;
        }
        ChatMessage aiMessage;
        if (generate) {
            billingService.chargeForGenerate(principal, session.getId());
            aiMessage = message(tenantId, session, ChatMessage.ROLE_AI,
                    aiJson(LlmResponse.ACTION_GENERATE, null, versions, null));
        } else {
            String question = response.getQuestion() == null || response.getQuestion().trim().isEmpty()
                    ? FALLBACK_QUESTION : response.getQuestion().trim();
            aiMessage = message(tenantId, session, ChatMessage.ROLE_AI,
                    aiJson(LlmResponse.ACTION_ASK, question, null, null));
        }

        // 5. 落 AI 消息并刷新会话要素上下文（同时刷新活跃时间）
        messageMapper.insert(aiMessage);
        sessionMapper.updateContext(tenantId, session.getId(), mergedContext);

        return reply(session, aiMessage, mergedContext);
    }

    @Transactional
    public ChatReplyVO revise(Long sessionId, ChatReviseRequest request) {
        AuthPrincipal principal = AuthContext.required();
        ChatSession session = sessionService.requireUsable(principal, sessionId);
        Long tenantId = principal.getTenantId();

        ChatMessage lastGenerate = messageMapper.findLatestGenerate(session.getId());
        if (lastGenerate == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "还没有可微调的文案版本");
        }
        List<String> baseVersions = versionsOf(lastGenerate.getContent());
        int versionNo = request.getVersionNo();
        if (versionNo < 1 || versionNo > baseVersions.size()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "版本号超出范围");
        }
        String baseText = baseVersions.get(versionNo - 1);

        LlmResponse response;
        try {
            response = llmProvider.complete(LlmRequest.revise(session.getScene(), session.getContext(),
                    baseText, request.getInstruction()));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "改写失败，请稍后重试");
        }
        List<String> revised = response.getVersions();
        if (revised == null || revised.isEmpty() || revised.get(0) == null || revised.get(0).trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "改写失败，请稍后重试");
        }
        String newText = complianceService.filterText(tenantId, revised.get(0));

        int reviseSeq = (session.getReviseCount() == null ? 0 : session.getReviseCount()) + 1;
        billingService.chargeForRevise(principal, session.getId(), reviseSeq);

        List<String> newVersions = new ArrayList<String>();
        newVersions.add(newText);
        ChatMessage aiMessage = message(tenantId, session, ChatMessage.ROLE_AI,
                aiJson(LlmResponse.ACTION_GENERATE, null, newVersions, versionNo));
        messageMapper.insert(aiMessage);
        sessionMapper.updateContext(tenantId, session.getId(),
                session.getContext() == null ? "{}" : session.getContext());
        sessionMapper.incrReviseCount(tenantId, session.getId());

        return reply(session, aiMessage, session.getContext() == null ? "{}" : session.getContext());
    }

    private ChatMessage message(Long tenantId, ChatSession session, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setTenantId(tenantId);
        message.setSessionId(session.getId());
        message.setUserId(session.getUserId());
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private ChatReplyVO reply(ChatSession session, ChatMessage aiMessage, String context) {
        ChatReplyVO vo = new ChatReplyVO();
        vo.setSessionId(session.getId());
        vo.setContext(context);
        vo.setMessageId(aiMessage.getId());
        try {
            JsonNode node = objectMapper.readTree(aiMessage.getContent());
            String action = node.path("action").asText("");
            vo.setAction(action);
            if (LlmResponse.ACTION_ASK.equals(action)) {
                vo.setQuestion(node.path("question").asText(FALLBACK_QUESTION));
            } else {
                List<String> versions = new ArrayList<String>();
                JsonNode array = node.path("versions");
                if (array.isArray()) {
                    array.forEach(item -> versions.add(item.asText("")));
                }
                vo.setVersions(versions);
            }
        } catch (Exception exception) {
            vo.setAction(LlmResponse.ACTION_ASK);
            vo.setQuestion(FALLBACK_QUESTION);
        }
        return vo;
    }

    private String historyJson(List<ChatMessage> recent) {
        try {
            List<Map<String, String>> history = new ArrayList<Map<String, String>>();
            for (ChatMessage item : recent) {
                Map<String, String> entry = new HashMap<String, String>();
                entry.put("role", item.getRole());
                entry.put("content", item.getContent());
                history.add(entry);
            }
            return objectMapper.writeValueAsString(history);
        } catch (Exception exception) {
            return "[]";
        }
    }

    /** 把 LLM 返回的要素补丁合并回会话上下文（浅合并，LLM 值覆盖旧值） */
    private String mergeContext(String contextJson, String patchJson) {
        try {
            JsonNode context = objectMapper.readTree(contextJson == null || contextJson.trim().isEmpty()
                    ? "{}" : contextJson);
            if (patchJson != null && !patchJson.trim().isEmpty()) {
                JsonNode patch = objectMapper.readTree(patchJson);
                if (patch.isObject()) {
                    Map<String, Object> merged = new HashMap<String, Object>();
                    context.fields().forEachRemaining(field -> merged.put(field.getKey(), field.getValue()));
                    patch.fields().forEachRemaining(field -> merged.put(field.getKey(), field.getValue().asText()));
                    return objectMapper.writeValueAsString(merged);
                }
            }
            return contextJson;
        } catch (Exception exception) {
            return contextJson == null ? "{}" : contextJson;
        }
    }

    private List<String> versionsOf(String aiContent) {
        try {
            JsonNode node = objectMapper.readTree(aiContent);
            List<String> versions = new ArrayList<String>();
            JsonNode array = node.path("versions");
            if (array.isArray()) {
                array.forEach(item -> versions.add(item.asText("")));
            }
            if (versions.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "历史消息里没有文案版本");
            }
            return versions;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "历史消息格式异常");
        }
    }

    private String aiJson(String action, String question, List<String> versions, Integer revisedFrom) {
        try {
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("action", action);
            if (question != null) {
                payload.put("question", question);
            }
            if (versions != null) {
                payload.put("versions", versions);
            }
            if (revisedFrom != null) {
                payload.put("revisedFrom", revisedFrom);
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            return "{\"action\":\"" + LlmResponse.ACTION_ASK + "\",\"question\":\"" + FALLBACK_QUESTION + "\"}";
        }
    }
}
