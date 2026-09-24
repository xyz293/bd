package com.xiaoa.ai.service;

import com.xiaoa.ai.model.Work;
import com.xiaoa.ai.model.WorkPublishStatus;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.mapper.WorkMapper;
import org.springframework.stereotype.Component;

/**
 * 作品发布状态流转守卫：所有状态变更必须走条件更新（UPDATE ... WHERE publish_status = from），
 * 行数为 0 即视为并发冲突（例如轮询线程与审核并发、双审），直接拒绝。
 */
@Component
public class WorkStateMachine {

    private final WorkMapper workMapper;

    public WorkStateMachine(WorkMapper workMapper) {
        this.workMapper = workMapper;
    }

    /**
     * 校验作品当前发布状态是否允许执行某操作（如改文案、重新生成）。
     */
    public void check(Work work, String... allowedStatuses) {
        String current = work.getPublishStatus() == null ? WorkPublishStatus.NONE : work.getPublishStatus();
        for (String allowed : allowedStatuses) {
            if (allowed.equals(current)) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_PARAMETER, "作品当前状态不支持该操作（" + current + "）");
    }

    /**
     * 条件更新流转，from -> to 必须是合法单向流转；失败说明状态已变更或非法流转。
     */
    public void transit(Long tenantId, Long workId, String from, String to) {
        if (!WorkPublishStatus.canTransit(from, to)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不允许的状态流转：" + from + " -> " + to);
        }
        if (workMapper.transitPublishStatus(tenantId, workId, from, to) != 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "作品状态已变更，请刷新后重试");
        }
    }
}
