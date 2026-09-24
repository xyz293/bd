package com.xiaoa.ai.service;

import com.xiaoa.ai.mapper.MediaTaskMapper;
import com.xiaoa.ai.model.AiRequest;
import com.xiaoa.ai.model.MediaTask;
import com.xiaoa.ai.model.Work;
import com.xiaoa.ai.provider.AiProvider;
import com.xiaoa.task.mapper.WorkMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 图文成套：生成成功后异步补配套文案。
 * 失败策略：caption 留空、不重试不告警，员工可通过 PUT /api/work/{id}/caption 手动补充。
 */
@Service
public class CaptionService {

    private final WorkMapper workMapper;
    private final MediaTaskMapper mediaTaskMapper;
    private final AiProvider aiProvider;
    private final Executor aiTaskExecutor;

    public CaptionService(WorkMapper workMapper, MediaTaskMapper mediaTaskMapper, AiProvider aiProvider,
                          @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
        this.workMapper = workMapper;
        this.mediaTaskMapper = mediaTaskMapper;
        this.aiProvider = aiProvider;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    /**
     * 在当前事务提交后异步触发，避免读到未提交数据，也不阻塞结算事务。
     */
    public void submitAfterCommit(Long tenantId, Long workId) {
        Runnable job = () -> {
            try {
                generateFor(tenantId, workId);
            } catch (RuntimeException ignored) {
                // 成套文案失败不重试不告警，caption 留空即可。
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(job);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(job);
            }
        });
    }

    private void dispatch(Runnable job) {
        try {
            aiTaskExecutor.execute(job);
        } catch (RejectedExecutionException ignored) {
            // 线程池已满时放弃本次文案补齐，员工可手动补文案。
        }
    }

    /**
     * 组装上下文（风格名 + 用户描述 + 生成提示词）调用文案生成，成功且原文案为空时回填。
     */
    public void generateFor(Long tenantId, Long workId) {
        Work work = workMapper.findById(workId, tenantId);
        if (work == null) {
            return;
        }
        AiRequest request = new AiRequest();
        request.setPrompt(buildContext(work));
        request.setStyleName(work.getStyleName());
        String caption = aiProvider.generateCaption(request);
        if (caption == null || caption.trim().isEmpty()) {
            return;
        }
        workMapper.fillCaptionIfAbsent(tenantId, workId, caption.trim());
    }

    private String buildContext(Work work) {
        StringBuilder context = new StringBuilder();
        String platform = work.getPlatform() == null ? "" : work.getPlatform().trim();
        if (!platform.isEmpty()) {
            context.append("发布平台：").append(platform).append("。");
        }
        String userInput = work.getUserInput() == null ? "" : work.getUserInput().trim();
        if (!userInput.isEmpty()) {
            context.append("内容描述：").append(userInput).append("。");
        }
        if (work.getMediaTaskId() != null) {
            MediaTask task = mediaTaskMapper.findById(work.getMediaTaskId());
            if (task != null && task.getPrompt() != null && !task.getPrompt().trim().isEmpty()) {
                context.append("生成提示词：").append(task.getPrompt().trim());
            }
        }
        return context.toString().trim();
    }
}
