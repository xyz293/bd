package com.xiaoa.asset.service;

import com.xiaoa.asset.model.ContentPackage;
import com.xiaoa.common.lock.RedisDistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 内容包定时下发：每分钟扫描一次，补发"今天所有到期未下发"的（防服务重启漏发）。
 * 幂等核心：先 tryExpire 条件更新抢占状态，抢到才建任务；建任务失败不回滚状态，记 last_error 告警人工补。
 */
@Component
public class ContentPackageDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(ContentPackageDispatchJob.class);

    /** 扫描级锁：多实例部署时只允许一个实例执行扫描；租期小于调度周期（1 分钟），实例崩溃后可自动恢复。 */
    private static final String JOB_LOCK_KEY = "job:content-package-dispatch";

    private final ContentPackageService contentPackageService;
    private final RedisDistributedLock distributedLock;

    public ContentPackageDispatchJob(ContentPackageService contentPackageService,
                                     RedisDistributedLock distributedLock) {
        this.contentPackageService = contentPackageService;
        this.distributedLock = distributedLock;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void dispatch() {
        String requestId = distributedLock.newRequestId();
        if (!distributedLock.tryLock(JOB_LOCK_KEY, requestId, 0, 50, TimeUnit.SECONDS)) {
            log.info("其他实例正在执行内容包下发，跳过本次调度");
            return;
        }
        try {
            List<ContentPackage> due = contentPackageService.selectDue(LocalDateTime.now());
            for (ContentPackage contentPackage : due) {
                if (contentPackageService.tryExpire(contentPackage.getId()) == 0) {
                    continue;
                }
                try {
                    Long taskId = contentPackageService.dispatchPackage(contentPackage);
                    log.info("内容包下发成功 packageId={} taskId={}", contentPackage.getId(), taskId);
                } catch (Exception exception) {
                    log.error("内容包下发失败 packageId={}", contentPackage.getId(), exception);
                    contentPackageService.markError(contentPackage.getId(), exception.getMessage());
                }
            }
        } finally {
            distributedLock.unlock(JOB_LOCK_KEY, requestId);
        }
    }
}
