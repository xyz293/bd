package com.xiaoa.admin.service;

import com.xiaoa.admin.dto.CreateExportRequest;
import com.xiaoa.admin.mapper.ExportTaskMapper;
import com.xiaoa.admin.model.ExportTask;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.common.lock.RedisDistributedLock;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ExportService {

    private final ExportTaskMapper exportTaskMapper;
    private final AdminPermissionService permissionService;
    private final RedisDistributedLock distributedLock;

    public ExportService(ExportTaskMapper exportTaskMapper, AdminPermissionService permissionService,
                         RedisDistributedLock distributedLock) {
        this.exportTaskMapper = exportTaskMapper;
        this.permissionService = permissionService;
        this.distributedLock = distributedLock;
    }

    /**
     * 创建导出任务：分布式锁保护「检查运行中数量 + 插入」的原子性，防止并发提交突破上限。
     *
     * <p>注意：锁必须加在事务外才有效（事务提交在方法返回后，锁内读到的 count 看不到未提交数据）。
     * 本方法仅单条 insert，自身原子，因此不再标注 {@code @Transactional}。</p>
     */
    public ExportTask create(CreateExportRequest request) {
        AuthPrincipal principal = permissionService.requiredWrite();
        String lockKey = "export:create:" + principal.getTenantId();
        String requestId = distributedLock.newRequestId();
        if (!distributedLock.tryLock(lockKey, requestId, 0, 10, TimeUnit.SECONDS)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "导出任务创建中，请勿重复提交");
        }
        try {
            if (exportTaskMapper.countRunning(principal.getTenantId()) >= 3) {
                throw new BusinessException(ErrorCode.DUPLICATE, "当前租户同时进行中的导出任务已达上限");
            }
            ExportTask task = new ExportTask();
            task.setTenantId(principal.getTenantId());
            task.setCreatedBy(principal.getUserId());
            task.setExportType(request.getExportType());
            task.setQueryParams(request.getQueryParams());
            task.setStatus(0);
            exportTaskMapper.insert(task);
            processAsync(task.getId(), task.getTenantId());
            return task;
        } finally {
            distributedLock.unlock(lockKey, requestId);
        }
    }

    public List<ExportTask> list() {
        return exportTaskMapper.findByTenant(permissionService.requiredRead().getTenantId());
    }

    public ExportTask get(Long id) {
        Long tenantId = permissionService.requiredRead().getTenantId();
        ExportTask task = exportTaskMapper.findById(tenantId, id);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导出任务不存在");
        }
        return task;
    }

    @Async("exportExecutor")
    public void processAsync(Long id, Long tenantId) {
        try {
            ExportTask task = exportTaskMapper.findById(tenantId, id);
            if (task == null) {
                return;
            }
            // 当前工程尚未接入 OSS，保留稳定的本地地址格式，后续仅替换存储实现。
            String fileUrl = "local://export/" + tenantId + "/" + id + ".csv";
            exportTaskMapper.finish(tenantId, id, 1, fileUrl, null);
        } catch (RuntimeException exception) {
            exportTaskMapper.finish(tenantId, id, 2, null,
                    exception.getMessage() == null ? "导出失败" : exception.getMessage());
        }
    }
}
