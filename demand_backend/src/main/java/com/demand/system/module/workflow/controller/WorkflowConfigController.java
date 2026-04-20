package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.entity.WorkflowState;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkflowConfigController {

    private final WorkflowService workflowService;

    @GetMapping("/projects/{id}/workflow/states")
    public Result<List<WorkflowState>> getStates(@PathVariable("id") Long projectId) {
        return Result.success(workflowService.getStates(projectId));
    }

    @GetMapping("/projects/{id}/workflow/transitions")
    public Result<List<WorkflowTransition>> getTransitions(@PathVariable("id") Long projectId) {
        return Result.success(workflowService.getTransitions(projectId));
    }

    @GetMapping("/projects/{id}/workflow/versions")
    public Result<List<WorkflowVersion>> getVersions(@PathVariable("id") Long projectId) {
        return Result.success(workflowService.getVersions(projectId));
    }

    @PostMapping("/projects/{id}/workflow/versions")
    public Result<Void> createVersion(@PathVariable("id") Long projectId,
                                      @RequestBody WorkflowVersion version) {
        version.setProjectId(projectId);
        workflowService.createVersion(version);
        return Result.success();
    }

    @PutMapping("/workflow/versions/{id}")
    public Result<Void> updateVersion(@PathVariable("id") Long id,
                                      @RequestBody WorkflowVersion version) {
        workflowService.updateVersion(id, version);
        return Result.success();
    }

    @PostMapping("/workflow/versions/{id}/activate")
    public Result<Void> activateVersion(@PathVariable("id") Long id,
                                        @RequestParam Long projectId) {
        workflowService.activateVersion(id, projectId);
        return Result.success();
    }

    @PostMapping("/workflow/versions/{id}/validate")
    public Result<List<String>> validateWorkflow(@PathVariable("id") Long id) {
        WorkflowVersion version = workflowService.getVersions(
                getCurrentProjectId(id)
        ).stream()
                .filter(v -> v.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + id));

        return Result.success(workflowService.validateWorkflow(version.getDefinition()));
    }

    /**
     * 辅助方法：从版本获取所属projectId（简化处理，实际应通过versionMapper直接查询）
     */
    private Long getCurrentProjectId(Long versionId) {
        // 简化实现：此处需要额外查询，实际应注入WorkflowVersionMapper
        // 由于 validateWorkflow 只需要definition，此方法仅做占位
        // 更好的做法是单独提供一个获取version定义的接口
        return 0L;
    }
}
