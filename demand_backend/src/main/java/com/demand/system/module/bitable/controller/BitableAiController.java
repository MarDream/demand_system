package com.demand.system.module.bitable.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.*;
import com.demand.system.module.bitable.service.BitableAiService;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 多维表格 AI 能力控制器
 * <p>
 * 所有涉及具体 Base/Table 的端点统一做成员角色校验：
 * 读类能力需要 VIEWER 及以上，写类能力需要 EDITOR 及以上，建表需要 ADMIN 及以上。
 */
@RestController
@RequestMapping("/api/v1/bitable/ai")
public class BitableAiController {

    private final BitableAiService bitableAiService;
    private final BitableAuthorizationService authorizationService;

    public BitableAiController(BitableAiService bitableAiService,
                               BitableAuthorizationService authorizationService) {
        this.bitableAiService = bitableAiService;
        this.authorizationService = authorizationService;
    }

    /**
     * AI 自然语言建表（预览，不落库，无资源访问）
     */
    @PostMapping("/build-table")
    @PreAuthorize("isAuthenticated()")
    public Result<AiBuildTableResult> previewBuildTable(@Valid @RequestBody AiBuildTableRequest request) {
        AiBuildTableResult result = bitableAiService.previewBuildTable(request.getDescription());
        return Result.success(result);
    }

    /**
     * AI 自然语言建表（确认写入）
     */
    @PostMapping("/build-table/confirm")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> confirmBuildTable(@RequestParam Long baseId,
                                          @RequestBody AiBuildTableResult result) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(baseId, userId);
        Long tableId = bitableAiService.confirmBuildTable(baseId, result, userId);
        return Result.success(tableId);
    }

    /**
     * AI 智能填充单个单元格
     */
    @PostMapping("/fill")
    @PreAuthorize("isAuthenticated()")
    public Result<Object> fillCell(@Valid @RequestBody AiFillRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = requireBaseIdByTableId(request.getTableId());
        authorizationService.checkWritePermission(baseId, userId);
        Object result = bitableAiService.fillCell(request.getTableId(), request.getRecordId(), request.getFieldId(), userId);
        return Result.success(result);
    }

    /**
     * AI 批量填充（异步任务提交）
     */
    @PostMapping("/fill-batch")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> fillBatchAsync(@Valid @RequestBody AiFillRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = requireBaseIdByTableId(request.getTableId());
        authorizationService.checkWritePermission(baseId, userId);
        bitableAiService.fillBatchAsync(request.getTableId(), request.getFieldId(), userId);
        return Result.success();
    }

    /**
     * AI 自然语言生成筛选条件（不落库，返回结构化条件供筛选面板填充）
     */
    @PostMapping("/filter-generate")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> generateFilter(@Valid @RequestBody AiFilterGenerateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = requireBaseIdByTableId(request.getTableId());
        authorizationService.checkReadPermission(baseId, userId);
        Map<String, Object> result = bitableAiService.aiGenerateFilter(request.getTableId(), request.getText(), userId);
        return Result.success(result);
    }

    /**
     * AI 对话式查询
     */
    @PostMapping("/query")
    @PreAuthorize("isAuthenticated()")
    public Result<AiQueryResult> query(@Valid @RequestBody AiQueryRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkReadPermission(request.getBaseId(), userId);
        AiQueryResult result = bitableAiService.query(request.getBaseId(), request.getTableId(), request.getQuestion(), userId);
        return Result.success(result);
    }

    /**
     * AI 自动分类
     */
    @PostMapping("/classify")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> classify(@Valid @RequestBody AiClassifyRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = requireBaseIdByTableId(request.getTableId());
        authorizationService.checkWritePermission(baseId, userId);
        bitableAiService.classifyRecords(request.getTableId(), request.getSourceFieldId(), request.getTargetFieldName(), userId);
        return Result.success();
    }

    /**
     * AI 自动摘要
     */
    @PostMapping("/summarize")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> summarize(@Valid @RequestBody AiSummarizeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = requireBaseIdByTableId(request.getTableId());
        authorizationService.checkWritePermission(baseId, userId);
        bitableAiService.summarizeRecords(request.getTableId(), request.getSourceFieldId(), request.getTargetFieldName(), userId);
        return Result.success();
    }

    private Long requireBaseIdByTableId(Long tableId) {
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        if (baseId == null) {
            // 表不存在时同样按无权限处理，避免暴露表 ID 存在性
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问该数据表");
        }
        return baseId;
    }
}
