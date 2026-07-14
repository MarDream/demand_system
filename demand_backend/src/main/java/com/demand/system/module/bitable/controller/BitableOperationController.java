package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.bitable.dto.BitableOperationVO;
import com.demand.system.module.bitable.service.BitableOperationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 多维表格操作历史控制器
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableOperationController {

    private final BitableOperationService bitableOperationService;

    public BitableOperationController(BitableOperationService bitableOperationService) {
        this.bitableOperationService = bitableOperationService;
    }

    /**
     * 查询多维表格容器的操作历史
     */
    @GetMapping("/bases/{baseId}/operations")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<BitableOperationVO>> listOperationsByBase(
            @PathVariable Long baseId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<BitableOperationVO> pageResult = bitableOperationService.listOperationsByBaseId(baseId, pageNum, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 按数据表查询操作历史
     * URL 中 baseId 通过查询参数传入（路由只有 tableId）
     */
    @GetMapping("/tables/{tableId}/operations")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<BitableOperationVO>> listOperationsByTable(
            @PathVariable Long tableId,
            @RequestParam Long baseId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        if (baseId == null) {
            return Result.fail("baseId 不能为空");
        }
        PageResult<BitableOperationVO> pageResult = bitableOperationService.listOperationsByTableId(baseId, tableId, pageNum, pageSize);
        return Result.success(pageResult);
    }
}
