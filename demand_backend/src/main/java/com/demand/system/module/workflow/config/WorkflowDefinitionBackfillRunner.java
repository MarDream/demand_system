package com.demand.system.module.workflow.config;

import com.demand.system.module.workflow.service.WorkflowDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 启动时回填历史工作流版本到独立的 workflow_definitions 实体。
 * <p>
 * 参照 {@code KnowledgeDocumentBackfillRunner} 模式：异步执行，失败不阻断启动。
 */
@Component
public class WorkflowDefinitionBackfillRunner {
    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionBackfillRunner.class);

    private final WorkflowDefinitionService workflowDefinitionService;

    public WorkflowDefinitionBackfillRunner(WorkflowDefinitionService workflowDefinitionService) {
        this.workflowDefinitionService = workflowDefinitionService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        try {
            int updatedCount = workflowDefinitionService.backfill();
            log.info("Workflow definition backfill finished, updated {} version records", updatedCount);
        } catch (Exception e) {
            log.warn("Workflow definition backfill failed", e);
        }
    }
}
