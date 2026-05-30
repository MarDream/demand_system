package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.CountersignRecordVO;
import com.demand.system.module.workflow.dto.CountersignSubmitDTO;
import com.demand.system.module.workflow.service.WorkflowCountersignService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflow/countersign")
public class WorkflowCountersignController {

    private final WorkflowCountersignService countersignService;

    public WorkflowCountersignController(WorkflowCountersignService countersignService) {
        this.countersignService = countersignService;
    }

    /**
     * 提交会签审批
     */
    @PostMapping("/submit")
    public Result<Void> submitCountersignApproval(@RequestBody CountersignSubmitDTO dto) {
        countersignService.submitCountersignApproval(dto);
        return Result.success();
    }

    /**
     * 获取会签记录列表
     */
    @GetMapping("/records")
    public Result<List<CountersignRecordVO>> getCountersignRecords(
            @RequestParam Long requirementId,
            @RequestParam String nodeId) {
        List<CountersignRecordVO> records = countersignService.getCountersignRecords(requirementId, nodeId);
        return Result.success(records);
    }

    /**
     * 检查当前用户是否可以会签
     */
    @GetMapping("/can-countersign")
    public Result<Boolean> canCurrentUserCountersign(
            @RequestParam Long requirementId,
            @RequestParam String nodeId) {
        boolean canCountersign = countersignService.canCurrentUserCountersign(requirementId, nodeId);
        return Result.success(canCountersign);
    }
}
