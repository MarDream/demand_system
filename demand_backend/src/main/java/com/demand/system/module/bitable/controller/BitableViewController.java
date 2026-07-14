package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableViewCreateDTO;
import com.demand.system.module.bitable.dto.BitableViewUpdateDTO;
import com.demand.system.module.bitable.dto.BitableViewVO;
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

    public BitableViewController(BitableViewService bitableViewService) {
        this.bitableViewService = bitableViewService;
    }

    @GetMapping("/tables/{tableId}/views")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableViewVO>> listViews(@PathVariable Long tableId) {
        List<BitableViewVO> list = bitableViewService.listViews(tableId);
        return Result.success(list);
    }

    @PostMapping("/tables/{tableId}/views")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createView(@PathVariable Long tableId,
                                   @Valid @RequestBody BitableViewCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long id = bitableViewService.createView(tableId, dto, userId);
        return Result.success(id);
    }

    @PutMapping("/views/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateView(@PathVariable Long id, @RequestBody BitableViewUpdateDTO dto) {
        bitableViewService.updateView(id, dto);
        return Result.success();
    }

    @DeleteMapping("/views/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteView(@PathVariable Long id) {
        bitableViewService.deleteView(id);
        return Result.success();
    }
}
