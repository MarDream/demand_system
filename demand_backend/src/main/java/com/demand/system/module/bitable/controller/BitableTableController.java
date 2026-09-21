package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableTableCreateDTO;
import com.demand.system.module.bitable.dto.BitableTableUpdateDTO;
import com.demand.system.module.bitable.dto.BitableTableVO;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableTableService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多维表格数据表控制器
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableTableController {

    private final BitableTableService bitableTableService;
    private final BitableAuthorizationService authorizationService;

    public BitableTableController(BitableTableService bitableTableService,
                                  BitableAuthorizationService authorizationService) {
        this.bitableTableService = bitableTableService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/bases/{baseId}/tables")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableTableVO>> listTables(@PathVariable Long baseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkReadPermission(baseId, userId);
        List<BitableTableVO> list = bitableTableService.listTables(baseId);
        return Result.success(list);
    }

    @PostMapping("/bases/{baseId}/tables")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createTable(@PathVariable Long baseId,
                                    @Valid @RequestBody BitableTableCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(baseId, userId);
        Long id = bitableTableService.createTable(baseId, dto, userId);
        return Result.success(id);
    }

    @PutMapping("/tables/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateTable(@PathVariable Long id, @RequestBody BitableTableUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(id);
        authorizationService.checkManagePermission(baseId, userId);
        bitableTableService.updateTable(id, dto, userId);
        return Result.success();
    }

    @DeleteMapping("/tables/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteTable(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(id);
        authorizationService.checkManagePermission(baseId, userId);
        bitableTableService.deleteTable(id, userId);
        return Result.success();
    }

    /**
     * 数据表同级排序（拖拽排序：按传入顺序回写 sort_order）。
     * 逐表校验管理权限，服务端再做同组一致性校验。
     */
    @PutMapping("/tables/sort")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> sortTables(@RequestBody List<Long> orderedIds) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (orderedIds != null) {
            for (Long tableId : orderedIds) {
                Long baseId = authorizationService.getBaseIdByTableId(tableId);
                authorizationService.checkManagePermission(baseId, userId);
            }
            bitableTableService.sortTables(orderedIds, userId);
        }
        return Result.success();
    }
}
