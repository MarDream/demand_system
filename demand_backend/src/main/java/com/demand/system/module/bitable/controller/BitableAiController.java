package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.*;
import com.demand.system.module.bitable.service.BitableAiService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 多维表格 AI 能力控制器
 */
@RestController
@RequestMapping("/api/v1/bitable/ai")
public class BitableAiController {

    private final BitableAiService bitableAiService;

    public BitableAiController(BitableAiService bitableAiService) {
        this.bitableAiService = bitableAiService;
    }

    /**
     * AI 自然语言建表（预览）
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
        bitableAiService.fillBatchAsync(request.getTableId(), request.getFieldId(), userId);
        return Result.success();
    }

    /**
     * AI 对话式查询
     */
    @PostMapping("/query")
    @PreAuthorize("isAuthenticated()")
    public Result<AiQueryResult> query(@Valid @RequestBody AiQueryRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
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
        bitableAiService.summarizeRecords(request.getTableId(), request.getSourceFieldId(), request.getTargetFieldName(), userId);
        return Result.success();
    }
}
