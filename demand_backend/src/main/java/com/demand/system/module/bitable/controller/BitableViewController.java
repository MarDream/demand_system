package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableViewCreateDTO;
import com.demand.system.module.bitable.dto.BitableViewUpdateDTO;
import com.demand.system.module.bitable.dto.BitableViewVO;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableViewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多维表格视图控制器
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableViewController {

    private final BitableViewService bitableViewService;
    private final BitableAuthorizationService authorizationService;

    public BitableViewController(BitableViewService bitableViewService,
                                 BitableAuthorizationService authorizationService) {
        this.bitableViewService = bitableViewService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/tables/{tableId}/views")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableViewVO>> listViews(@PathVariable Long tableId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        authorizationService.checkReadPermission(baseId, userId);
        List<BitableViewVO> list = bitableViewService.listViews(tableId);
        return Result.success(list);
    }

    @PostMapping("/tables/{tableId}/views")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createView(@PathVariable Long tableId,
                                   @Valid @RequestBody BitableViewCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        authorizationService.checkWritePermission(baseId, userId);
        Long id = bitableViewService.createView(tableId, dto, userId);
        return Result.success(id);
    }

    @PatchMapping("/views/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateView(@PathVariable Long id, @RequestBody BitableViewUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByViewId(id);
        authorizationService.checkWritePermission(baseId, userId);
        bitableViewService.updateView(id, dto);
        return Result.success();
    }

    @PostMapping("/views/{viewId}/duplicate")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> duplicateView(@PathVariable Long viewId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByViewId(viewId);
        authorizationService.checkWritePermission(baseId, userId);
        Long newId = bitableViewService.duplicateView(viewId, userId);
        return Result.success(newId);
    }

    @PostMapping("/tables/{tableId}/default-view/{viewId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> setDefaultView(@PathVariable Long tableId, @PathVariable Long viewId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        authorizationService.checkWritePermission(baseId, userId);
        bitableViewService.setDefaultView(tableId, viewId);
        return Result.success();
    }

    @DeleteMapping("/views/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteView(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByViewId(id);
        authorizationService.checkWritePermission(baseId, userId);
        bitableViewService.deleteView(id);
        return Result.success();
    }
}
