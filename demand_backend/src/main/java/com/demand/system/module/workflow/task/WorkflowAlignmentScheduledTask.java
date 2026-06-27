package com.demand.system.module.workflow.task;

import com.demand.system.module.workflow.service.WorkflowRuntimeMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 工作流版本对齐定时任务
 * 
 * 性能优化：将工作流版本对齐从同步调用改为异步定时任务
 * - 原问题：每次查询"我的待办"列表都同步执行对齐，导致2-5秒阻塞
 * - 优化方案：每分钟后台自动对齐一次，列表查询不再阻塞
 * - 预期收益：列表查询性能提升50%+
 * 
 * @author Senior Developer
 * @date 2026-06-26
 */
@Component
public class WorkflowAlignmentScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAlignmentScheduledTask.class);

    private final WorkflowRuntimeMigrationService workflowRuntimeMigrationService;

    public WorkflowAlignmentScheduledTask(WorkflowRuntimeMigrationService workflowRuntimeMigrationService) {
        this.workflowRuntimeMigrationService = workflowRuntimeMigrationService;
    }

    /**
     * 定时对齐运行中的工作流实例到最新版本
     * 
     * 执行频率：每60秒执行一次（fixedDelay表示上次执行完成后延迟60秒再执行）
     * 初始延迟：应用启动后30秒开始首次执行（避免启动时过载）
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void alignWorkflowVersions() {
        try {
            log.debug("开始执行工作流版本对齐定时任务");
            long startTime = System.currentTimeMillis();
            
            workflowRuntimeMigrationService.alignRunningInstancesToActiveVersion();
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("工作流版本对齐定时任务完成，耗时: {}ms", duration);
            
            // 如果对齐耗时超过5秒，记录警告日志
            if (duration > 5000) {
                log.warn("工作流版本对齐耗时过长: {}ms，可能需要进一步优化", duration);
            }
        } catch (Exception e) {
            log.error("工作流版本对齐定时任务执行失败", e);
            // 不抛出异常，避免影响后续定时任务执行
        }
    }
}
