package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.CreateMigrationPlanRequest;
import com.demand.system.module.workflow.dto.MigrationPlanVO;
import com.demand.system.module.workflow.dto.MigrationPreviewVO;
import com.demand.system.module.workflow.dto.MigrationResultDTO;
import com.demand.system.module.workflow.entity.WorkflowMigrationLog;
import com.demand.system.module.workflow.entity.WorkflowMigrationPlan;
import com.demand.system.module.workflow.service.WorkflowMigrationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流版本迁移计划控制器（ADR-002）
 *
 * 仅超级管理员可操作迁移计划。
 * 四步流程：选择版本→配置映射→预检→执行
 */
@RestController
@RequestMapping("/api/v1/admin/workflow-migration/plans")
public class WorkflowMigrationPlanController {

    private final WorkflowMigrationService workflowMigrationService;

    public WorkflowMigrationPlanController(WorkflowMigrationService workflowMigrationService) {
        this.workflowMigrationService = workflowMigrationService;
    }

    /**
     * 创建迁移计划（草稿状态，含自动建议的节点映射）
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<MigrationPlanVO> createPlan(@RequestBody CreateMigrationPlanRequest request) {
        return Result.success(workflowMigrationService.createMigrationPlan(request));
    }

    /**
     * 查询迁移计划列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<List<MigrationPlanVO>> listPlans(@RequestParam(required = false) Long projectId) {
        return Result.success(workflowMigrationService.listMigrationPlans(projectId));
    }

    /**
     * 查询迁移计划详情
     */
    @GetMapping("/{planId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<MigrationPlanVO> getPlan(@PathVariable Long planId) {
        return Result.success(workflowMigrationService.getMigrationPlan(planId));
    }

    /**
     * 更新节点映射配置
     */
    @PutMapping("/{planId}/mapping")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<MigrationPlanVO> updateMapping(@PathVariable Long planId,
                                                  @RequestBody List<WorkflowMigrationPlan.NodeMappingItem> mapping) {
        return Result.success(workflowMigrationService.updateNodeMapping(planId, mapping));
    }

    /**
     * 预检迁移计划（不执行，只返回影响分析）
     */
    @PostMapping("/{planId}/preview")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<MigrationPreviewVO> preview(@PathVariable Long planId) {
        return Result.success(workflowMigrationService.previewMigration(planId));
    }

    /**
     * 执行迁移计划
     */
    @PostMapping("/{planId}/execute")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<MigrationResultDTO> execute(@PathVariable Long planId) {
        return Result.success(workflowMigrationService.executeMigration(planId));
    }

    /**
     * 查询迁移日志
     */
    @GetMapping("/{planId}/logs")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<List<WorkflowMigrationLog>> listLogs(@PathVariable Long planId) {
        return Result.success(workflowMigrationService.listMigrationLogs(planId));
    }
}
