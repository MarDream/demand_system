package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableAutomationCreateDTO;
import com.demand.system.module.bitable.dto.BitableAutomationUpdateDTO;
import com.demand.system.module.bitable.dto.BitableAutomationVO;
import com.demand.system.module.bitable.entity.BitableAutomationRun;
import com.demand.system.module.bitable.mapper.BitableAutomationRunMapper;
import com.demand.system.module.bitable.service.BitableAutomationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多维表格-自动化规则控制器
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableAutomationController {

    private final BitableAutomationService automationService;
    private final BitableAutomationRunMapper runMapper;

    public BitableAutomationController(BitableAutomationService automationService,
                                       BitableAutomationRunMapper runMapper) {
        this.automationService = automationService;
        this.runMapper = runMapper;
    }

    @GetMapping("/bases/{baseId}/automations")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableAutomationVO>> listAutomations(@PathVariable Long baseId) {
        List<BitableAutomationVO> list = automationService.listAutomations(baseId);
        return Result.success(list);
    }

    @PostMapping("/bases/{baseId}/automations")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createAutomation(@PathVariable Long baseId,
                                         @Valid @RequestBody BitableAutomationCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long id = automationService.createAutomation(baseId, dto, userId);
        return Result.success(id);
    }

    @PatchMapping("/automations/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateAutomation(@PathVariable Long id,
                                         @RequestBody BitableAutomationUpdateDTO dto) {
        automationService.updateAutomation(id, dto);
        return Result.success();
    }

    @DeleteMapping("/automations/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteAutomation(@PathVariable Long id) {
        automationService.deleteAutomation(id);
        return Result.success();
    }

    @PostMapping("/automations/{id}/toggle")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> toggleAutomation(@PathVariable Long id,
                                         @RequestParam boolean enabled) {
        automationService.toggleAutomation(id, enabled);
        return Result.success();
    }

    @GetMapping("/automations/{id}/runs")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<BitableAutomationRun>> listAutomationRuns(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        int total = runMapper.countByAutomationId(id);
        int offset = (pageNum - 1) * pageSize;
        List<BitableAutomationRun> runs = runMapper.selectByAutomationId(id, offset, pageSize);
        PageResult<BitableAutomationRun> pageResult = new PageResult<>(runs, total, pageNum, pageSize);
        return Result.success(pageResult);
    }
}
