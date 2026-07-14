package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableRecordVO;
import com.demand.system.module.bitable.dto.CalendarViewData;
import com.demand.system.module.bitable.dto.FormulaEvaluateRequest;
import com.demand.system.module.bitable.dto.GanttViewData;
import com.demand.system.module.bitable.dto.GalleryViewData;
import com.demand.system.module.bitable.dto.LinkRecordsRequest;
import com.demand.system.module.bitable.service.BitableAdvancedViewService;
import com.demand.system.module.bitable.service.BitableFormulaService;
import com.demand.system.module.bitable.service.BitableLinkService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多维表格高级功能控制器
 * - 高级视图（甘特/日历/画廊）
 * - 关联字段（link）
 * - 公式字段（formula/rollup/lookup）
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableAdvancedController {

    private final BitableAdvancedViewService advancedViewService;
    private final BitableLinkService linkService;
    private final BitableFormulaService formulaService;

    public BitableAdvancedController(BitableAdvancedViewService advancedViewService,
                                     BitableLinkService linkService,
                                     BitableFormulaService formulaService) {
        this.advancedViewService = advancedViewService;
        this.linkService = linkService;
        this.formulaService = formulaService;
    }

    // ==================== 高级视图 ====================

    /**
     * 获取甘特视图数据
     */
    @GetMapping("/views/{viewId}/gantt")
    @PreAuthorize("isAuthenticated()")
    public Result<GanttViewData> getGanttView(@PathVariable Long viewId,
                                              @RequestParam Long tableId) {
        GanttViewData data = advancedViewService.getGanttView(viewId, tableId);
        return Result.success(data);
    }

    /**
     * 获取日历视图数据
     */
    @GetMapping("/views/{viewId}/calendar")
    @PreAuthorize("isAuthenticated()")
    public Result<CalendarViewData> getCalendarView(@PathVariable Long viewId,
                                                    @RequestParam Long tableId) {
        CalendarViewData data = advancedViewService.getCalendarView(viewId, tableId);
        return Result.success(data);
    }

    /**
     * 获取画廊视图数据
     */
    @GetMapping("/views/{viewId}/gallery")
    @PreAuthorize("isAuthenticated()")
    public Result<GalleryViewData> getGalleryView(@PathVariable Long viewId,
                                                  @RequestParam Long tableId) {
        GalleryViewData data = advancedViewService.getGalleryView(viewId, tableId);
        return Result.success(data);
    }

    // ==================== 关联字段 ====================

    /**
     * 获取可关联的记录列表
     */
    @GetMapping("/tables/{tableId}/linkable-records")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableRecordVO>> listLinkableRecords(@PathVariable Long tableId,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false, defaultValue = "50") Integer pageSize) {
        List<BitableRecordVO> records = linkService.listLinkableRecords(tableId, keyword, pageSize);
        return Result.success(records);
    }

    /**
     * 创建关联关系
     */
    @PostMapping("/fields/{fieldId}/link")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> linkRecords(@PathVariable Long fieldId,
                                    @Valid @RequestBody LinkRecordsRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        linkService.linkRecords(fieldId, request.getRecordId(), request.getTargetRecordIds(), userId);
        return Result.success();
    }

    /**
     * 获取关联记录ID列表
     */
    @GetMapping("/fields/{fieldId}/records/{recordId}/linked")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Long>> getLinkedRecordIds(@PathVariable Long fieldId,
                                                 @PathVariable Long recordId) {
        List<Long> ids = linkService.getLinkedRecordIds(fieldId, recordId);
        return Result.success(ids);
    }

    // ==================== 公式字段 ====================

    /**
     * 计算公式（测试用）
     */
    @PostMapping("/records/{recordId}/formula")
    @PreAuthorize("isAuthenticated()")
    public Result<Object> evaluateFormula(@PathVariable Long recordId,
                                          @Valid @RequestBody FormulaEvaluateRequest request) {
        Object result = formulaService.evaluateFormula(request.getFormula(), request.getFieldValues());
        return Result.success(result);
    }
}