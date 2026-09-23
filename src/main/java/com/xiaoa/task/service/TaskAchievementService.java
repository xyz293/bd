package com.xiaoa.task.service;

import com.xiaoa.common.auth.AdminDataScopeService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.mapper.TaskAnalyticsMapper;
import com.xiaoa.task.model.TaskBadge;
import com.xiaoa.task.model.TaskRankingItem;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskAchievementService {

    private final TaskAnalyticsMapper taskMapper;
    private final AdminDataScopeService scopeService;

    public TaskAchievementService(TaskAnalyticsMapper taskMapper, AdminDataScopeService scopeService) {
        this.taskMapper = taskMapper;
        this.scopeService = scopeService;
    }

    public List<TaskRankingItem> ranking(String scope, String period, Long requestedStoreId, Integer requestedLimit) {
        AuthPrincipal principal = scopeService.requiredTaskRead();
        String normalizedScope = normalize(scope, "STORE");
        String normalizedPeriod = normalize(period, "WEEK");
        int limit = requestedLimit == null ? 50 : Math.min(100, Math.max(1, requestedLimit));
        if ("NATIONAL".equals(normalizedScope)
                && !"HQ_ADMIN".equals(principal.getRole()) && !"VIEWER".equals(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有全域管理员可以查看全国榜");
        }
        LocalDate[] range = range(normalizedPeriod, LocalDate.now());
        Long storeId = requestedStoreId;
        if ("OWNER".equals(principal.getRole()) || "STAFF".equals(principal.getRole())) {
            if (storeId != null && !storeId.equals(principal.getOrgId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看本店排行榜");
            }
            storeId = principal.getOrgId();
        } else if (storeId != null) {
            scopeService.requireStoreReadable(storeId);
        }
        Long regionId = "REGION_ADMIN".equals(principal.getRole()) ? principal.getOrgId() : null;
        List<TaskRankingItem> result = taskMapper.findRanking(principal.getTenantId(), range[0], range[1],
                "NATIONAL".equals(normalizedScope) ? null : storeId, regionId, limit);
        for (int index = 0; index < result.size(); index++) {
            result.get(index).setRank(index + 1);
        }
        return result;
    }

    public List<TaskBadge> badges(Long requestedUserId, LocalDate requestedDate) {
        AuthPrincipal principal = scopeService.requiredTaskRead();
        Long userId = requestedUserId == null ? principal.getUserId() : requestedUserId;
        if (!scopeService.canViewUser(principal, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该员工徽章");
        }
        LocalDate date = requestedDate == null ? LocalDate.now() : requestedDate;
        LocalDate weekStart = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = date.withDayOfMonth(1);
        long weekExpected = taskMapper.countUserExpected(principal.getTenantId(), userId, weekStart, date.plusDays(1));
        long weekFinished = taskMapper.countUserFinished(principal.getTenantId(), userId, weekStart, date.plusDays(1));
        long monthExpected = taskMapper.countUserExpected(principal.getTenantId(), userId, monthStart, date.plusDays(1));
        long monthFinished = taskMapper.countUserFinished(principal.getTenantId(), userId, monthStart, date.plusDays(1));
        long consecutiveDays = taskMapper.countFinishedDays(principal.getTenantId(), userId, date.minusDays(6), date.plusDays(1));
        Long regionId = "REGION_ADMIN".equals(principal.getRole()) ? principal.getOrgId() : null;
        List<TaskRankingItem> monthRanking = taskMapper.findRanking(principal.getTenantId(), monthStart,
                date.plusDays(1), null, regionId, 1000);
        boolean monthStar = !monthRanking.isEmpty() && userId.equals(monthRanking.get(0).getUserId());
        Long userStoreId = scopeService.storeIdOfUser(principal.getTenantId(), userId);
        List<TaskRankingItem> storeRanking = userStoreId == null ? new ArrayList<TaskRankingItem>()
                : taskMapper.findRanking(principal.getTenantId(), monthStart, date.plusDays(1), userStoreId, null, 1000);
        boolean storeFirst = !storeRanking.isEmpty() && userId.equals(storeRanking.get(0).getUserId());
        List<TaskBadge> badges = new ArrayList<>();
        badges.add(new TaskBadge("STREAK_7_DAYS", "连续打卡达人", "连续 7 天至少完成一项任务",
                consecutiveDays >= 7, consecutiveDays, 7));
        double weekRate = weekExpected == 0 ? 0D : (double) weekFinished / weekExpected;
        badges.add(new TaskBadge("WEEK_100_PERCENT", "周任务全勤", "本周任务完成率达到 100%",
                weekExpected > 0 && weekFinished == weekExpected, weekRate, 1));
        badges.add(new TaskBadge("MONTH_TASK_STAR", "月度任务之星", "本月完成任务数在可见范围内排名第一",
                monthStar, monthFinished, 1));
        badges.add(new TaskBadge("STORE_TASK_STAR", "门店完成率第一", "本月门店员工完成率排名第一",
                storeFirst, monthExpected == 0 ? 0D : (double) monthFinished / monthExpected, 1));
        return badges;
    }

    private LocalDate[] range(String period, LocalDate date) {
        if ("MONTH".equals(period)) {
            LocalDate start = date.withDayOfMonth(1);
            return new LocalDate[]{start, start.plusMonths(1)};
        }
        LocalDate start = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new LocalDate[]{start, start.plusDays(7)};
    }

    private String normalize(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim().toUpperCase();
    }
}
