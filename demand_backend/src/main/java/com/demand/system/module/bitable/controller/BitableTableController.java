package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableTableCreateDTO;
import com.demand.system.module.bitable.dto.BitableTableUpdateDTO;
import com.demand.system.module.bitable.dto.BitableTableVO;
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

    public BitableTableController(BitableTableService bitableTableService) {
        this.bitableTableService = bitableTableService;
    }

    @GetMapping("/bases/{baseId}/tables")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableTableVO>> listTables(@PathVariable Long baseId) {
        List<BitableTableVO> list = bitableTableService.listTables(baseId);
        return Result.success(list);
    }

    @PostMapping("/bases/{baseId}/tables")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createTable(@PathVariable Long baseId,
                                    @Valid @RequestBody BitableTableCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long id = bitableTableService.createTable(baseId, dto, userId);
        return Result.success(id);
    }

    @PutMapping("/tables/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateTable(@PathVariable Long id, @RequestBody BitableTableUpdateDTO dto) {
        bitableTableService.updateTable(id, dto);
        return Result.success();
    }

    @DeleteMapping("/tables/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteTable(@PathVariable Long id) {
        bitableTableService.deleteTable(id);
        return Result.success();
    }
}
