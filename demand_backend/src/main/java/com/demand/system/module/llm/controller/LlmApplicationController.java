package com.demand.system.module.llm.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.llm.dto.LlmApplicationMoveGroupDTO;
import com.demand.system.module.llm.dto.LlmApplicationUpdateDTO;
import com.demand.system.module.llm.service.LlmApplicationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/llm-applications")
public class LlmApplicationController {
    private final LlmApplicationService applicationService;

    public LlmApplicationController(LlmApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<?> list() {
        return Result.success(applicationService.list());
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public Result<?> update(@PathVariable String code, @RequestBody LlmApplicationUpdateDTO dto) {
        return Result.success(applicationService.update(code, dto));
    }

    /**
     * 调整功能点所属分组（groupId 为 null 表示移出分组）
     */
    @PutMapping("/{code}/group")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public Result<?> moveToGroup(@PathVariable String code, @RequestBody LlmApplicationMoveGroupDTO dto) {
        return Result.success(applicationService.moveToGroup(code, dto == null ? null : dto.getGroupId()));
    }
}
