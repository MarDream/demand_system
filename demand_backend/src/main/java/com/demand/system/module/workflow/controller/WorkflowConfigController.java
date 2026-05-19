package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.entity.WorkflowState;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyAuthority('admin', 'workflow:config')")
public class WorkflowConfigController {

    private final WorkflowService workflowService;
    private final WorkflowVersionMapper workflowVersionMapper;

    public WorkflowConfigController(WorkflowService workflowService, WorkflowVersionMapper workflowVersionMapper) {
        this.workflowService = workflowService;
        this.workflowVersionMapper = workflowVersionMapper;
    }

    @GetMapping("/projects/{id}/workflow/states")
    public Result<List<WorkflowState>> getStates(@PathVariable("id") Long projectId) {
        return Result.success(workflowService.getStates(projectId));
    }

    @PostMapping("/projects/{id}/workflow/states")
    public Result<WorkflowState> createState(@PathVariable("id") Long projectId,
                                            @RequestBody WorkflowState state) {
        return Result.success(workflowService.createState(projectId, state));
    }

    @PutMapping("/workflow/states/{id}")
    public Result<Void> updateState(@PathVariable("id") Long id,
                                   @RequestBody WorkflowState state) {
        workflowService.updateState(id, state);
        return Result.success();
    }

    @DeleteMapping("/workflow/states/{id}")
    public Result<Void> deleteState(@PathVariable("id") Long id) {
        workflowService.deleteState(id);
        return Result.success();
    }

    @GetMapping("/projects/{id}/workflow/transitions")
    public Result<List<WorkflowTransition>> getTransitions(@PathVariable("id") Long projectId) {
        return Result.success(workflowService.getTransitions(projectId));
    }

    @PostMapping("/projects/{id}/workflow/transitions")
    public Result<WorkflowTransition> createTransition(@PathVariable("id") Long projectId,
                                                      @RequestBody WorkflowTransition transition) {
        return Result.success(workflowService.createTransition(projectId, transition));
    }

    @PutMapping("/workflow/transitions/{id}")
    public Result<Void> updateTransition(@PathVariable("id") Long id,
                                        @RequestBody WorkflowTransition transition) {
        workflowService.updateTransition(id, transition);
        return Result.success();
    }

    @DeleteMapping("/workflow/transitions/{id}")
    public Result<Void> deleteTransition(@PathVariable("id") Long id) {
        workflowService.deleteTransition(id);
        return Result.success();
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
        WorkflowVersion version = workflowVersionMapper.selectById(id);
        if (version == null) {
            throw new IllegalArgumentException("Version not found: " + id);
        }
        return Result.success(workflowService.validateWorkflow(version.getDefinition()));
    }
}
