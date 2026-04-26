package com.demand.system.module.workflow.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.workflow.dto.TransitionRequest;
import com.demand.system.module.workflow.dto.TransitionResponse;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/requirements")
@RequiredArgsConstructor
public class WorkflowTransitionController {

    private final WorkflowService workflowService;

    @GetMapping("/{id}/available-transitions")
    public Result<List<WorkflowTransition>> available(@PathVariable("id") Long requirementId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return Result.success(workflowService.getAvailableTransitions(requirementId, userId));
    }

    @PostMapping("/{id}/transition")
    public Result<TransitionResponse> transition(@PathVariable("id") Long requirementId,
                                                 @RequestBody TransitionRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        TransitionResponse response = workflowService.executeTransition(
                requirementId,
                request.getTargetStateId(),
                userId,
                request.getComment()
        );
        return Result.success(response);
    }
}
