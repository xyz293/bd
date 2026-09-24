package com.xiaoa.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.admin.mapper.AssetAdminMapper;
import com.xiaoa.ai.dto.GenerateRequest;
import com.xiaoa.ai.mapper.MediaTaskMapper;
import com.xiaoa.task.mapper.WorkMapper;
import com.xiaoa.ai.model.AiRequest;
import com.xiaoa.ai.model.AiResult;
import com.xiaoa.ai.model.MediaTask;
import com.xiaoa.ai.model.Work;
import com.xiaoa.ai.provider.AiProvider;
import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.quota.service.QuotaService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.scheduling.annotation.Scheduled;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class AiGatewayService {

    private final WorkMapper workMapper;
    private final MediaTaskMapper mediaTaskMapper;
    private final PromptService promptService;
    private final AiProvider aiProvider;
    private final ObjectStorageService objectStorageService;
    private final AiTaskSettlementService settlementService;
    private final QuotaService quotaService;
    private final AssetAdminMapper assetAdminMapper;
    private final ObjectMapper objectMapper;
    private final Executor aiTaskExecutor;
    private final long imageCost;
    private final long videoCost;

    public AiGatewayService(WorkMapper workMapper, MediaTaskMapper mediaTaskMapper,
                            PromptService promptService, AiProvider aiProvider,
                            ObjectStorageService objectStorageService,
                            AiTaskSettlementService settlementService, QuotaService quotaService,
                            AssetAdminMapper assetAdminMapper, ObjectMapper objectMapper,
                            @Value("${xiaoa.ai.cost.image:1}") long imageCost,
                            @Value("${xiaoa.ai.cost.video:5}") long videoCost,
                            @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
        this.workMapper = workMapper;
        this.mediaTaskMapper = mediaTaskMapper;
        this.promptService = promptService;
        this.aiProvider = aiProvider;
        this.objectStorageService = objectStorageService;
        this.settlementService = settlementService;
        this.quotaService = quotaService;
        this.assetAdminMapper = assetAdminMapper;
        this.objectMapper = objectMapper;
        this.imageCost = imageCost;
        this.videoCost = videoCost;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    @Transactional
    public Work generate(GenerateRequest request) {
        AuthPrincipal principal = AuthContext.required();
        String type = normalizeType(request.getType());
        String scene = type;
        PromptService.PromptBuildResult prompt = promptService.build(principal.getTenantId(), scene,
                request.getPlatform(), request.getStyleId(), request.getProductName(), request.getUserInput());
        long cost = "IMAGE".equals(type) ? imageCost : videoCost;

        Work work = new Work();
        work.setTenantId(principal.getTenantId());
        work.setUserId(principal.getUserId());
        work.setType(type);
        work.setPlatform(request.getPlatform());
        work.setStyleId(request.getStyleId());
        work.setStyleName(prompt.getStyle().getName());
        work.setUserInput(request.getUserInput());
        work.setRefImageUrls(joinUrls(request.getRefImageUrls()));
        work.setPromptTemplateId(prompt.getTemplate().getId());
        work.setPromptTemplateVersion(prompt.getTemplate().getVersion());
        work.setStatus("PENDING");
        work.setSourceAssetIds(snapshotAssets(principal.getTenantId(), request.getAssetIds()));
        workMapper.insert(work);

        quotaService.consumeForAi(principal.getTenantId(), principal.getOrgId(), cost, work.getId());

        MediaTask task = new MediaTask();
        task.setTenantId(principal.getTenantId());
        task.setUserId(principal.getUserId());
        task.setWorkId(work.getId());
        task.setStoreId(principal.getOrgId());
        task.setType(type);
        task.setScene(scene);
        task.setPrompt(prompt.getPrompt());
        task.setCost(cost);
        mediaTaskMapper.insert(task);
        workMapper.bindTask(principal.getTenantId(), work.getId(), task.getId());
        dispatchAfterCommit(task.getId());
        return workMapper.findById(work.getId(), principal.getTenantId());
    }

    public Work get(Long workId) {
        AuthPrincipal principal = AuthContext.required();
        Work work = workMapper.findById(workId, principal.getTenantId());
        if (work == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作品不存在");
        }
        if (!principal.getUserId().equals(work.getUserId()) && !isAdmin(principal)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该作品");
        }
        return work;
    }

    public List<MediaTask> adminTasks(Long tenantId, int limit) {
        return mediaTaskMapper.findForAdmin(tenantId, Math.min(Math.max(limit, 1), 200));
    }

    public void dispatch(Long taskId) {
        try {
            aiTaskExecutor.execute(() -> processTask(taskId));
        } catch (RejectedExecutionException ignored) {
            // 有界线程池拒绝时不改表状态，任务由定时兜底扫描重新派发。
        }
    }

    /**
     * 事务提交后派发生成任务；供改稿等复用同一异步通道。
     */
    public void dispatchAfterCommit(Long taskId) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(taskId);
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatch(taskId);
                    }
                });
    }

    private void processTask(Long taskId) {
        MediaTask task = findTask(taskId);
        if (task == null || !"PENDING".equals(task.getStatus()) || mediaTaskMapper.claimPending(taskId) != 1) {
            return;
        }
        task = findTask(taskId);
        try {
            AiRequest request = buildAiRequest(task);
            if ("IMAGE".equals(task.getType())) {
                processImage(task, request);
            } else if ("VIDEO".equals(task.getType())) {
                String externalTaskId = aiProvider.submitVideoTask(request);
                if (externalTaskId == null || externalTaskId.trim().isEmpty()) {
                    throw new IllegalStateException("模型未返回视频任务ID");
                }
                mediaTaskMapper.markSubmitted(task.getId(), externalTaskId);
            } else {
                throw new IllegalStateException("不支持的生成类型");
            }
        } catch (RuntimeException exception) {
            settlementService.failure(task, message(exception));
        }
    }

    private AiRequest buildAiRequest(MediaTask task) {
        AiRequest request = new AiRequest();
        request.setPrompt(task.getPrompt());
        Work work = workMapper.findById(task.getWorkId(), task.getTenantId());
        if (work != null) {
            request.setStyleName(work.getStyleName());
            request.setRefImageUrls(splitUrls(work.getRefImageUrls()));
        }
        return request;
    }

    private void processImage(MediaTask task, AiRequest request) {
        AiResult result = aiProvider.generateImage(request);
        if (!result.isSuccess()) {
            settlementService.failure(task, result.getFailReason() == null ? result.getFailCode() : result.getFailReason());
            return;
        }
        settlementSuccessWithRetry(task, result.getContentUrl());
    }

    @Scheduled(fixedDelayString = "${xiaoa.ai.schedule.video-poll-ms:30000}")
    public void pollVideoTasks() {
        for (MediaTask candidate : mediaTaskMapper.findSubmittedVideos(100)) {
            if (mediaTaskMapper.claimSubmitted(candidate.getId()) == 1) {
                pollVideo(candidate);
            }
        }
    }

    private void pollVideo(MediaTask task) {
        try {
            AiResult result = aiProvider.queryVideoTask(task.getProviderTaskId());
            if (result.isProcessing()) {
                mediaTaskMapper.resumeSubmitted(task.getId());
            } else if (result.isSuccess()) {
                settlementSuccessWithRetry(task, result.getContentUrl());
            } else {
                settlementService.failure(task, result.getFailReason() == null ? result.getFailCode() : result.getFailReason());
            }
        } catch (RuntimeException exception) {
            settlementService.failure(task, message(exception));
        }
    }

    @Scheduled(fixedDelayString = "${xiaoa.ai.schedule.timeout-sweeper-ms:300000}")
    public void sweepTasks() {
        java.time.LocalDateTime deadline = java.time.LocalDateTime.now().minusMinutes(30);
        for (MediaTask task : mediaTaskMapper.findTimeouts(deadline, 100)) {
            settlementService.failure(task, "模型处理超时");
        }
        for (MediaTask task : mediaTaskMapper.findPending(100)) {
            dispatch(task.getId());
        }
        for (MediaTask task : mediaTaskMapper.findFailedWithoutRefund(100)) {
            settlementService.failure(task, task.getErrorMessage());
        }
    }

    private void settlementSuccessWithRetry(MediaTask task, String sourceUrl) {
        RuntimeException last = null;
        for (int i = 0; i < 3; i++) {
            try {
                String contentUrl = objectStorageService.transfer(task.getTenantId(), task.getWorkId(), sourceUrl);
                settlementService.success(task, contentUrl);
                return;
            } catch (RuntimeException exception) {
                last = exception;
            }
        }
        settlementService.failure(task, last == null ? "成品转存失败" : message(last));
    }

    private MediaTask findTask(Long taskId) {
        return mediaTaskMapper.findById(taskId);
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if (!"IMAGE".equals(normalized) && !"VIDEO".equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "生成类型仅支持 IMAGE 或 VIDEO");
        }
        return normalized;
    }

    private String joinUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        return String.join("\n", urls);
    }

    private List<String> splitUrls(String urls) {
        if (urls == null || urls.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.asList(urls.split("\\n"));
    }

    private String message(Throwable exception) {
        return exception.getMessage() == null ? "AI模型调用失败" : exception.getMessage();
    }

    /**
     * 素材可见性校验：assetIds 必须在当前租户可见范围（平台挂载包 owner_type=2 / 本租户自有素材），
     * 校验通过后把素材 ID 列表快照进 work.source_asset_ids。
     */
    private String snapshotAssets(Long tenantId, java.util.List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return null;
        }
        java.util.LinkedHashSet<Long> distinct = new java.util.LinkedHashSet<>(assetIds);
        distinct.remove(null);
        if (distinct.isEmpty()) {
            return null;
        }
        for (Long assetId : distinct) {
            if (assetAdminMapper.findVisibleById(tenantId, assetId) == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "引用素材不存在或不在可见范围：" + assetId);
            }
        }
        try {
            return objectMapper.writeValueAsString(distinct);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "素材快照序列化失败");
        }
    }

    private boolean isAdmin(AuthPrincipal principal) {
        return "HQ_ADMIN".equals(principal.getRole()) || "REGION_ADMIN".equals(principal.getRole())
                || "VIEWER".equals(principal.getRole());
    }
}
