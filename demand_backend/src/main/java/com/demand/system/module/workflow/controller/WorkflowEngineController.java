package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.WorkflowAvailableActionsDTO;
import com.demand.system.module.workflow.dto.FlowTransitionRequest;
import com.demand.system.module.workflow.dto.TransitionVO;
import com.demand.system.module.workflow.service.WorkflowEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflow-engine")
@Tag(name = "工作流引擎", description = "需求流转引擎")
public class WorkflowEngineController {

    private final WorkflowEngineService engineService;

    public WorkflowEngineController(WorkflowEngineService engineService) {
        this.engineService = engineService;
    }

    @PostMapping("/init")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "初始化工作流实例")
    public Result<Void> initWorkflow(@RequestParam Long requirementId,
                                     @RequestParam Long workflowVersionId) {
        engineService.initWorkflow(requirementId, workflowVersionId);
        return Result.success();
    }

    @PostMapping("/transition")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "流转到下一节点")
    public Result<Void> transition(@Valid @RequestBody FlowTransitionRequest request) {
        engineService.transition(request);
        return Result.success();
    }

    @PostMapping("/rollback/{requirementId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "回退到上一节点")
    public Result<Void> rollback(@PathVariable Long requirementId,
                                  @RequestParam(required = false) String comment) {
        engineService.rollback(requirementId, comment);
        return Result.success();
    }

    @PostMapping("/cancel/{requirementId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "取消需求")
    public Result<Void> cancel(@PathVariable Long requirementId,
                                @RequestParam(required = false) String comment) {
        engineService.cancel(requirementId, comment);
        return Result.success();
    }

    @PostMapping("/draft/{requirementId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "保存为草稿")
    public Result<Void> saveDraft(@PathVariable Long requirementId) {
        engineService.saveDraft(requirementId);
        return Result.success();
    }

    @GetMapping("/actions/{requirementId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取当前需求可执行动作")
    public Result<WorkflowAvailableActionsDTO> getAvailableActions(@PathVariable Long requirementId) {
        return Result.success(engineService.getAvailableActions(requirementId));
    }

    @GetMapping("/transitions/{requirementId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取流转记录")
    public Result<List<TransitionVO>> getTransitionHistory(@PathVariable Long requirementId) {
        return Result.success(engineService.getTransitionHistory(requirementId));
    }
}
