package com.xiaoa.common.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.model.Task;
import com.xiaoa.tenant.mapper.OrgMapper;
import com.xiaoa.tenant.mapper.UserOrgRoleMapper;
import com.xiaoa.tenant.model.Org;
import com.xiaoa.tenant.model.UserOrgRole;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 企业版管理端统一的数据范围判断，避免不同模块各自实现一套不一致的区域过滤规则。
 */
@Service
public class AdminDataScopeService {

    private final OrgMapper orgMapper;
    private final UserOrgRoleMapper userOrgRoleMapper;
    private final ObjectMapper objectMapper;

    public AdminDataScopeService(OrgMapper orgMapper, UserOrgRoleMapper userOrgRoleMapper,
                                 ObjectMapper objectMapper) {
        this.orgMapper = orgMapper;
        this.userOrgRoleMapper = userOrgRoleMapper;
        this.objectMapper = objectMapper;
    }

    public AuthPrincipal requiredTaskRead() {
        AuthPrincipal principal = AuthContext.required();
        if (!isTaskReadableRole(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色不能查看任务数据");
        }
        return principal;
    }

    public boolean isTaskReadableRole(String role) {
        return "HQ_ADMIN".equals(role) || "REGION_ADMIN".equals(role)
                || "VIEWER".equals(role) || "OWNER".equals(role) || "STAFF".equals(role);
    }

    public Long regionIdFor(AuthPrincipal principal) {
        return "REGION_ADMIN".equals(principal.getRole()) ? principal.getOrgId() : null;
    }

    public List<Long> visibleStoreIds(AuthPrincipal principal) {
        if (principal == null || principal.getTenantId() == null) {
            return Collections.emptyList();
        }
        if ("OWNER".equals(principal.getRole()) || "STAFF".equals(principal.getRole())) {
            return principal.getOrgId() == null
                    ? Collections.<Long>emptyList() : Collections.singletonList(principal.getOrgId());
        }
        List<Long> storeIds = new ArrayList<>();
        for (Org org : orgMapper.findByTenantId(principal.getTenantId())) {
            if (org.getType() == null || org.getType() != 3) {
                continue;
            }
            if (!"REGION_ADMIN".equals(principal.getRole())
                    || principal.getOrgId().equals(org.getParentId())) {
                storeIds.add(org.getId());
            }
        }
        return storeIds;
    }

    public void requireStoreReadable(Long storeId) {
        AuthPrincipal principal = requiredTaskRead();
        if (!canViewStore(principal, storeId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该门店数据");
        }
    }

    public boolean canViewStore(AuthPrincipal principal, Long storeId) {
        if (principal == null || storeId == null || principal.getTenantId() == null) {
            return false;
        }
        if ("HQ_ADMIN".equals(principal.getRole()) || "VIEWER".equals(principal.getRole())) {
            return findStore(principal.getTenantId(), storeId) != null;
        }
        if ("OWNER".equals(principal.getRole()) || "STAFF".equals(principal.getRole())) {
            return storeId.equals(principal.getOrgId()) && findStore(principal.getTenantId(), storeId) != null;
        }
        Org store = findStore(principal.getTenantId(), storeId);
        return "REGION_ADMIN".equals(principal.getRole()) && store != null
                && principal.getOrgId().equals(store.getParentId());
    }

    public boolean canViewTask(AuthPrincipal principal, Task task) {
        if (principal == null || task == null || !principal.getTenantId().equals(task.getTenantId())) {
            return false;
        }
        if ("HQ_ADMIN".equals(principal.getRole()) || "VIEWER".equals(principal.getRole())) {
            return true;
        }
        if (task.getTargetScope() == null || task.getTargetScope() == 1) {
            return true;
        }
        List<Long> targetIds = parseIds(task.getTargetIds());
        if ("REGION_ADMIN".equals(principal.getRole())) {
            if (task.getTargetScope() == 2) {
                return targetIds.contains(principal.getOrgId());
            }
            if (task.getTargetScope() == 3) {
                for (Long storeId : targetIds) {
                    if (canViewStore(principal, storeId)) {
                        return true;
                    }
                }
                return false;
            }
            return hasUserInVisibleStore(principal, targetIds);
        }
        if ("OWNER".equals(principal.getRole()) || "STAFF".equals(principal.getRole())) {
            if (task.getTargetScope() == 3) {
                return targetIds.contains(principal.getOrgId());
            }
            if (task.getTargetScope() == 4) {
                return targetIds.contains(principal.getUserId());
            }
            Org store = findStore(principal.getTenantId(), principal.getOrgId());
            return task.getTargetScope() == 2 && store != null && targetIds.contains(store.getParentId());
        }
        return false;
    }

    public boolean canViewUser(AuthPrincipal principal, Long userId) {
        if (principal == null || userId == null) {
            return false;
        }
        if (principal.getUserId().equals(userId)) {
            return true;
        }
        if ("HQ_ADMIN".equals(principal.getRole()) || "VIEWER".equals(principal.getRole())) {
            return belongsToTenant(principal.getTenantId(), userId);
        }
        for (UserOrgRole role : userOrgRoleMapper.findActiveByTenantId(principal.getTenantId())) {
            if (role.getUserId().equals(userId) && canViewStore(principal, role.getOrgId())) {
                return true;
            }
        }
        return false;
    }

    public Long storeIdOfUser(Long tenantId, Long userId) {
        for (UserOrgRole role : userOrgRoleMapper.findActiveByTenantId(tenantId)) {
            if (userId.equals(role.getUserId())) {
                Org org = findStore(tenantId, role.getOrgId());
                if (org != null) {
                    return org.getId();
                }
            }
        }
        return null;
    }

    private boolean hasUserInVisibleStore(AuthPrincipal principal, List<Long> userIds) {
        for (UserOrgRole role : userOrgRoleMapper.findActiveByTenantId(principal.getTenantId())) {
            if (userIds.contains(role.getUserId()) && canViewStore(principal, role.getOrgId())) {
                return true;
            }
        }
        return false;
    }

    private boolean belongsToTenant(Long tenantId, Long userId) {
        for (UserOrgRole role : userOrgRoleMapper.findActiveByTenantId(tenantId)) {
            if (userId.equals(role.getUserId())) {
                return true;
            }
        }
        return false;
    }

    private Org findStore(Long tenantId, Long storeId) {
        Org org = orgMapper.findById(storeId, tenantId);
        return org != null && org.getType() != null && org.getType() == 3 ? org : null;
    }

    private List<Long> parseIds(String json) {
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "任务目标范围格式错误");
        }
    }
}
