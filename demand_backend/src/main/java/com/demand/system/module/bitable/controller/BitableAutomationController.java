package com.demand.system.module.bitable.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableAutomationCreateDTO;
import com.demand.system.module.bitable.dto.BitableAutomationUpdateDTO;
import com.demand.system.module.bitable.dto.BitableAutomationVO;
import com.demand.system.module.bitable.entity.BitableAutomation;
import com.demand.system.module.bitable.entity.BitableAutomationRun;
import com.demand.system.module.bitable.mapper.BitableAutomationMapper;
import com.demand.system.module.bitable.mapper.BitableAutomationRunMapper;
import com.demand.system.module.bitable.service.BitableAutomationService;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
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
    private final BitableAutomationMapper automationMapper;
    private final BitableAuthorizationService authorizationService;

    public BitableAutomationController(BitableAutomationService automationService,
                                       BitableAutomationRunMapper runMapper,
                                       BitableAutomationMapper automationMapper,
                                       BitableAuthorizationService authorizationService) {
        this.automationService = automationService;
        this.runMapper = runMapper;
        this.automationMapper = automationMapper;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/bases/{baseId}/automations")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableAutomationVO>> listAutomations(@PathVariable Long baseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkReadPermission(baseId, userId);
        List<BitableAutomationVO> list = automationService.listAutomations(baseId);
        return Result.success(list);
    }

    @PostMapping("/bases/{baseId}/automations")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createAutomation(@PathVariable Long baseId,
                                         @Valid @RequestBody BitableAutomationCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(baseId, userId);
        Long id = automationService.createAutomation(baseId, dto, userId);
        return Result.success(id);
    }

    @PatchMapping("/automations/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateAutomation(@PathVariable Long id,
                                         @RequestBody BitableAutomationUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = requireBaseIdByAutomationId(id);
        authorizationService.checkManagePermission(baseId, userId);
        automationService.updateAutomation(id, dto);
        return Result.success();
    }

    @DeleteMapping("/automations/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteAutomation(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = requireBaseIdByAutomationId(id);
        authorizationService.checkManagePermission(baseId, userId);
        automationService.deleteAutomation(id);
        return Result.success();
    }

    @PostMapping("/automations/{id}/toggle")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> toggleAutomation(@PathVariable Long id,
                                         @RequestParam boolean enabled) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = requireBaseIdByAutomationId(id);
        authorizationService.checkManagePermission(baseId, userId);
        automationService.toggleAutomation(id, enabled);
        return Result.success();
    }

    @GetMapping("/automations/{id}/runs")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<BitableAutomationRun>> listAutomationRuns(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = requireBaseIdByAutomationId(id);
        authorizationService.checkReadPermission(baseId, userId);
        int page = pageNum != null && pageNum > 0 ? pageNum : 1;
        int size = pageSize != null && pageSize > 0 ? Math.min(pageSize, 100) : 20;
        int total = runMapper.countByAutomationId(id);
        int offset = (page - 1) * size;
        List<BitableAutomationRun> runs = runMapper.selectByAutomationId(id, offset, size);
        PageResult<BitableAutomationRun> pageResult = new PageResult<>(runs, total, page, size);
        return Result.success(pageResult);
    }

    /**
     * 按自动化ID反查所属 Base，实体不存在时按无权限处理，避免向外部泄露 ID 存在性
     */
    private Long requireBaseIdByAutomationId(Long automationId) {
        BitableAutomation automation = automationMapper.selectById(automationId);
        if (automation == null || automation.getBaseId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问该自动化规则");
        }
        return automation.getBaseId();
    }
}
