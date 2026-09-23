package com.xiaoa.task.service;

import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.dto.RemindTaskRequest;
import com.xiaoa.task.mapper.MessageMapper;
import com.xiaoa.task.mapper.TaskRecordMapper;
import com.xiaoa.task.model.Message;
import com.xiaoa.task.model.Task;
import com.xiaoa.task.model.TaskRecord;
import com.xiaoa.tenant.mapper.UserOrgRoleMapper;
import com.xiaoa.tenant.model.UserOrgRole;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class TaskReminderService {

    private final TaskService taskService;
    private final TaskRecordMapper recordMapper;
    private final MessageMapper messageMapper;
    private final UserOrgRoleMapper userOrgRoleMapper;

    public TaskReminderService(TaskService taskService, TaskRecordMapper recordMapper,
                               MessageMapper messageMapper, UserOrgRoleMapper userOrgRoleMapper) {
        this.taskService = taskService;
        this.recordMapper = recordMapper;
        this.messageMapper = messageMapper;
        this.userOrgRoleMapper = userOrgRoleMapper;
    }

    @Transactional
    public int remind(RemindTaskRequest request) {
        AuthPrincipal principal = AuthContext.required();
        Task task = taskService.get(request.getTaskId());
        if (!canRemind(principal, task)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LocalDate periodDate = request.getPeriodDate() == null
                ? taskService.periodDate(task.getFrequency(), LocalDate.now()) : request.getPeriodDate();
        int sent = 0;
        for (UserOrgRole role : userOrgRoleMapper.findActiveByTenantId(principal.getTenantId())) {
            if (!"STAFF".equals(role.getRole()) && !"OWNER".equals(role.getRole())) {
                continue;
            }
            if (task.getTargetScope() == 3 && !contains(task.getTargetIds(), role.getOrgId())) {
                continue;
            }
            if (task.getTargetScope() == 4 && !contains(task.getTargetIds(), role.getUserId())) {
                continue;
            }
            TaskRecord record = recordMapper.findOne(principal.getTenantId(), task.getId(), role.getUserId(), periodDate);
            if (record == null || record.getStatus() == 0) {
                Message message = new Message();
                message.setTenantId(principal.getTenantId());
                message.setUserId(role.getUserId());
                message.setType("TASK_REMINDER");
                message.setTitle("任务提醒");
                message.setContent("任务「" + task.getTitle() + "」尚未完成，请及时处理");
                messageMapper.insert(message);
                sent++;
            }
        }
        return sent;
    }

    @Scheduled(cron = "0 0 17 * * ?")
    public void remindDailyTasks() {
        // 统一提醒入口由管理端按租户触发；定时任务保留为后续租户遍历实现的调度点。
    }

    private boolean canRemind(AuthPrincipal principal, Task task) {
        if ("HQ_ADMIN".equals(principal.getRole()) || "REGION_ADMIN".equals(principal.getRole())) {
            return true;
        }
        return "OWNER".equals(principal.getRole()) && contains(task.getTargetIds(), principal.getOrgId());
    }

    private boolean contains(String json, Long id) {
        return json != null && json.contains(String.valueOf(id));
    }
}
