package com.xiaoa.admin.service;

import com.xiaoa.ai.model.Work;
import com.xiaoa.ai.model.WorkPublishStatus;
import com.xiaoa.ai.service.WorkStateMachine;
import com.xiaoa.common.auth.AdminDataScopeService;
import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.mapper.AuditRecordMapper;
import com.xiaoa.task.mapper.MessageMapper;
import com.xiaoa.task.mapper.WorkMapper;
import com.xiaoa.task.model.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 作品审核：OWNER 查本店、REGION_ADMIN 查本区域、HQ_ADMIN 查全域，
 * 数据范围统一接入 AdminDataScopeService；所有状态变更走状态机条件更新防并发双审。
 */
@Service
public class AuditService {

    private static final int MAX_PENDING_LIMIT = 100;

    private final WorkMapper workMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final MessageMapper messageMapper;
    private final WorkStateMachine stateMachine;
    private final AdminDataScopeService dataScopeService;

    public AuditService(WorkMapper workMapper, AuditRecordMapper auditRecordMapper, MessageMapper messageMapper,
                        WorkStateMachine stateMachine, AdminDataScopeService dataScopeService) {
        this.workMapper = workMapper;
        this.auditRecordMapper = auditRecordMapper;
        this.messageMapper = messageMapper;
        this.stateMachine = stateMachine;
        this.dataScopeService = dataScopeService;
    }

    public List<Work> pending() {
        AuthPrincipal principal = requireAuditor();
        List<Long> visibleStoreIds = dataScopeService.visibleStoreIds(principal);
        List<Work> works = workMapper.findPendingAudit(principal.getTenantId(), MAX_PENDING_LIMIT);
        List<Work> visible = new ArrayList<>();
        for (Work work : works) {
            Long ownerStoreId = dataScopeService.storeIdOfUser(principal.getTenantId(), work.getUserId());
            if (ownerStoreId != null && visibleStoreIds.contains(ownerStoreId)) {
                visible.add(work);
            }
        }
        return visible;
    }

    @Transactional
    public void approve(Long workId) {
        Work work = requireAuditable(workId);
        stateMachine.transit(work.getTenantId(), work.getId(),
                WorkPublishStatus.PENDING_AUDIT, WorkPublishStatus.APPROVED);
        auditRecordMapper.insert(work.getTenantId(), work.getId(), AuthContext.required().getUserId(), "PASS", null);
    }

    @Transactional
    public void reject(Long workId, String opinion) {
        AuthPrincipal principal = AuthContext.required();
        Work work = requireAuditable(workId);
        stateMachine.transit(work.getTenantId(), work.getId(),
                WorkPublishStatus.PENDING_AUDIT, WorkPublishStatus.REJECTED);
        auditRecordMapper.insert(work.getTenantId(), work.getId(), principal.getUserId(), "REJECT", opinion);
        Message message = new Message();
        message.setTenantId(work.getTenantId());
        message.setUserId(work.getUserId());
        message.setType("WORK_AUDIT");
        message.setTitle("作品审核驳回");
        message.setContent("作品被驳回：" + opinion);
        messageMapper.insert(message);
    }

    private AuthPrincipal requireAuditor() {
        AuthPrincipal principal = AuthContext.required();
        if (!"HQ_ADMIN".equals(principal.getRole()) && !"REGION_ADMIN".equals(principal.getRole())
                && !"OWNER".equals(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色不能审核作品");
        }
        return principal;
    }

    private Work requireAuditable(Long workId) {
        AuthPrincipal principal = requireAuditor();
        Work work = workMapper.findById(workId, principal.getTenantId());
        if (work == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作品不存在");
        }
        Long ownerStoreId = dataScopeService.storeIdOfUser(principal.getTenantId(), work.getUserId());
        if (ownerStoreId == null || !dataScopeService.visibleStoreIds(principal).contains(ownerStoreId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权审核该作品");
        }
        if (!WorkPublishStatus.PENDING_AUDIT.equals(work.getPublishStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "作品不在待审核状态");
        }
        return work;
    }
}
