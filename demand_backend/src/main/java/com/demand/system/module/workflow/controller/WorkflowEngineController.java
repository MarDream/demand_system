package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.FlowTransitionRequest;
import com.demand.system.module.workflow.dto.TransitionVO;
import com.demand.system.module.workflow.service.WorkflowEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflow-engine")
@RequiredArgsConstructor
@Tag(name = "工作流引擎", description = "需求流转引擎")
public class WorkflowEngineController {

    private final WorkflowEngineService engineService;

    @PostMapping("/init")
    @Operation(summary = "初始化工作流实例")
    public Result<Void> initWorkflow(@RequestParam Long requirementId,
                                     @RequestParam Long workflowVersionId) {
        engineService.initWorkflow(requirementId, workflowVersionId);
        return Result.success();
    }

    @PostMapping("/transition")
    @Operation(summary = "流转到下一节点")
    public Result<Void> transition(@Valid @RequestBody FlowTransitionRequest request) {
        engineService.transition(request);
        return Result.success();
    }

    @PostMapping("/rollback/{requirementId}")
    @Operation(summary = "回退到上一节点")
    public Result<Void> rollback(@PathVariable Long requirementId,
                                  @RequestParam(required = false) String comment) {
        engineService.rollback(requirementId, comment);
        return Result.success();
    }

    @PostMapping("/cancel/{requirementId}")
    @Operation(summary = "取消需求")
    public Result<Void> cancel(@PathVariable Long requirementId,
                                @RequestParam(required = false) String comment) {
        engineService.cancel(requirementId, comment);
        return Result.success();
    }

    @PostMapping("/draft/{requirementId}")
    @Operation(summary = "保存为草稿")
    public Result<Void> saveDraft(@PathVariable Long requirementId) {
        engineService.saveDraft(requirementId);
        return Result.success();
    }

    @GetMapping("/transitions/{requirementId}")
    @Operation(summary = "获取流转记录")
    public Result<List<TransitionVO>> getTransitionHistory(@PathVariable Long requirementId) {
        return Result.success(engineService.getTransitionHistory(requirementId));
    }
}
