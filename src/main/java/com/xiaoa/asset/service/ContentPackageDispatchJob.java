package com.xiaoa.asset.service;

import com.xiaoa.asset.model.ContentPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容包定时下发：每分钟扫描一次，补发"今天所有到期未下发"的（防服务重启漏发）。
 * 幂等核心：先 tryExpire 条件更新抢占状态，抢到才建任务；建任务失败不回滚状态，记 last_error 告警人工补。
 */
@Component
public class ContentPackageDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(ContentPackageDispatchJob.class);

    private final ContentPackageService contentPackageService;

    public ContentPackageDispatchJob(ContentPackageService contentPackageService) {
        this.contentPackageService = contentPackageService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void dispatch() {
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
    }
}
