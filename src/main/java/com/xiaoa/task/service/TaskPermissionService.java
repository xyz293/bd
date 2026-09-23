package com.xiaoa.task.service;

import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.dto.CreateTaskRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class TaskPermissionService {

    public void validateCreate(AuthPrincipal principal, CreateTaskRequest request) {
        if (!"HQ_ADMIN".equals(principal.getRole()) && !"REGION_ADMIN".equals(principal.getRole())
                && !"OWNER".equals(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色不能创建任务");
        }
        int level = levelOf(principal);
        if (request.getTargetScope() == null || request.getTargetScope() < level) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务下发范围超出当前角色权限");
        }
        if (level == 3 && request.getTargetScope() != 3) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "店长只能创建本店任务");
        }
        if (level == 2 && request.getTargetScope() == 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "区域管理员不能创建全域任务");
        }
        if (level == 2 && request.getTargetScope() == 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "区域管理员只能下发到本区域及以下对象");
        }
        if (level == 4) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "员工不能创建任务");
        }
        if (request.getTargetScope() != 1 && (request.getTargetIds() == null || request.getTargetIds().isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "指定范围任务必须传入目标对象");
        }
        if (level == 3 && request.getTargetIds() != null
                && (request.getTargetIds().size() != 1 || !principal.getOrgId().equals(request.getTargetIds().get(0)))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "店长只能选择本店");
        }
    }

    public void validateTaskAccess(AuthPrincipal principal, com.xiaoa.task.model.Task task) {
        if (!principal.getTenantId().equals(task.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (levelOf(principal) > task.getCreatedLevel() && task.getTargetScope() != 3) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public boolean canModify(AuthPrincipal principal, com.xiaoa.task.model.Task task) {
        if (!principal.getTenantId().equals(task.getTenantId())) {
            return false;
        }
        if ("HQ_ADMIN".equals(principal.getRole())) {
            return true;
        }
        if ("REGION_ADMIN".equals(principal.getRole())) {
            return task.getCreatedLevel() >= 2;
        }
        if ("OWNER".equals(principal.getRole()) && task.getCreatedLevel() <= 2) {
            if (task.getTargetScope() == null || task.getTargetScope() == 1) {
                return true;
            }
            return task.getTargetIds() != null && task.getTargetIds().contains(String.valueOf(principal.getOrgId()));
        }
        return false;
    }

    public int levelOf(AuthPrincipal principal) {
        if ("HQ_ADMIN".equals(principal.getRole()) || "VIEWER".equals(principal.getRole())) {
            return 1;
        }
        if ("REGION_ADMIN".equals(principal.getRole())) {
            return 2;
        }
        if ("OWNER".equals(principal.getRole())) {
            return 3;
        }
        return 4;
    }

    public List<Long> normalizeTargetIds(CreateTaskRequest request) {
        return request.getTargetIds() == null ? Collections.emptyList() : request.getTargetIds();
    }
}
