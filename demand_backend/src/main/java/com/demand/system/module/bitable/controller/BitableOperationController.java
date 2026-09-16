package com.demand.system.module.bitable.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableOperationQueryDTO;
import com.demand.system.module.bitable.dto.BitableOperationVO;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableOperationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 多维表格操作历史控制器
 * 参考飞书多维表格"操作记录"：所有协作者（只读及以上）可见，支持按类型/操作人/时间筛选
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
     * 查询多维表格容器的操作历史（协作者可见）
     */
    @GetMapping("/bases/{baseId}/operations")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<BitableOperationVO>> listOperationsByBase(
            @PathVariable Long baseId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        authorizationService.checkReadPermission(baseId, currentUserId);
        BitableOperationQueryDTO query = buildQuery(pageNum, pageSize, operationType, userId, startTime, endTime);
        PageResult<BitableOperationVO> pageResult = bitableOperationService.listOperationsByBaseId(baseId, query);
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
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        if (baseId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问该数据表");
        }
        authorizationService.checkReadPermission(baseId, currentUserId);
        BitableOperationQueryDTO query = buildQuery(pageNum, pageSize, operationType, userId, startTime, endTime);
        PageResult<BitableOperationVO> pageResult =
                bitableOperationService.listOperationsByTableId(baseId, tableId, query);
        return Result.success(pageResult);
    }

    private BitableOperationQueryDTO buildQuery(Integer pageNum, Integer pageSize, String operationType,
                                                Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        BitableOperationQueryDTO query = new BitableOperationQueryDTO();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        if (operationType != null && !operationType.isBlank()) {
            List<String> types = Arrays.stream(operationType.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            query.setOperationTypes(types);
        }
        query.setUserId(userId);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        return query;
    }
}
