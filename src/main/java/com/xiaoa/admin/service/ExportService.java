package com.xiaoa.admin.service;

import com.xiaoa.admin.dto.CreateExportRequest;
import com.xiaoa.admin.mapper.ExportTaskMapper;
import com.xiaoa.admin.model.ExportTask;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExportService {

    private final ExportTaskMapper exportTaskMapper;
    private final AdminPermissionService permissionService;

    public ExportService(ExportTaskMapper exportTaskMapper, AdminPermissionService permissionService) {
        this.exportTaskMapper = exportTaskMapper;
        this.permissionService = permissionService;
    }

    @Transactional
    public ExportTask create(CreateExportRequest request) {
        AuthPrincipal principal = permissionService.requiredWrite();
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
