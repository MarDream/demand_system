package com.demand.system.module.bitable.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableOperationVO;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
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
    private final BitableAuthorizationService authorizationService;

    public BitableOperationController(BitableOperationService bitableOperationService,
                                      BitableAuthorizationService authorizationService) {
        this.bitableOperationService = bitableOperationService;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询多维表格容器的操作历史（ADMIN 及以上可见）
     */
    @GetMapping("/bases/{baseId}/operations")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<BitableOperationVO>> listOperationsByBase(
            @PathVariable Long baseId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(baseId, userId);
        PageResult<BitableOperationVO> pageResult = bitableOperationService.listOperationsByBaseId(baseId, pageNum, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 按数据表查询操作历史，baseId 一律由 tableId 反查，不信任前端传参
     */
    @GetMapping("/tables/{tableId}/operations")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<BitableOperationVO>> listOperationsByTable(
            @PathVariable Long tableId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        if (baseId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问该数据表");
        }
        authorizationService.checkManagePermission(baseId, userId);
        PageResult<BitableOperationVO> pageResult = bitableOperationService.listOperationsByTableId(baseId, tableId, pageNum, pageSize);
        return Result.success(pageResult);
    }
}
