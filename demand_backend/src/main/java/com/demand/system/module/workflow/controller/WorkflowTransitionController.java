package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.TransitionRequest;
import com.demand.system.module.workflow.dto.TransitionResponse;
import com.demand.system.module.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/requirements")
@RequiredArgsConstructor
public class WorkflowTransitionController {

    private final WorkflowService workflowService;

    @PostMapping("/{id}/transition")
    public Result<TransitionResponse> transition(@PathVariable("id") Long requirementId,
                                                 @RequestBody TransitionRequest request) {
        // TODO: 从SecurityUtils.getCurrentUserId()获取真实用户ID
        Long userId = 1L;
        TransitionResponse response = workflowService.executeTransition(
                requirementId,
                request.getTargetStateId(),
                userId,
                request.getComment()
        );
        return Result.success(response);
    }
}
