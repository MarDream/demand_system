package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.ParallelBranchVO;
import com.demand.system.module.workflow.service.WorkflowParallelBranchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflow/parallel")
public class WorkflowParallelBranchController {

    private final WorkflowParallelBranchService parallelBranchService;

    public WorkflowParallelBranchController(WorkflowParallelBranchService parallelBranchService) {
        this.parallelBranchService = parallelBranchService;
    }

    @GetMapping("/branches")
    public Result<List<ParallelBranchVO>> listBranches(@RequestParam Long requirementId) {
        return Result.success(parallelBranchService.listByRequirementId(requirementId));
    }

    @PostMapping("/switch")
    public Result<Void> switchBranch(@RequestParam Long requirementId, @RequestParam Long branchId) {
        parallelBranchService.switchActiveBranch(requirementId, branchId);
        return Result.success();
    }
}
