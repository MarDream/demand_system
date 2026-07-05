package com.demand.system.module.requirement.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.requirement.dto.RequirementCreateDTO;
import com.demand.system.module.requirement.dto.RequirementDetailVO;
import com.demand.system.module.requirement.dto.RequirementApprovalEvaluationVO;
import com.demand.system.module.requirement.dto.RequirementApprovalSupplementCreateDTO;
import com.demand.system.module.requirement.dto.RequirementCommentCreateDTO;
import com.demand.system.module.requirement.dto.RequirementCommentVO;
import com.demand.system.module.requirement.dto.RequirementDraftCreateDTO;
import com.demand.system.module.requirement.dto.RequirementDraftUpdateDTO;
import com.demand.system.module.requirement.dto.RequirementMyListQueryDTO;
import com.demand.system.module.requirement.dto.RequirementQueryDTO;
import com.demand.system.module.requirement.dto.RequirementSubmitDTO;
import com.demand.system.module.requirement.dto.NextNodeOptionDTO;
import com.demand.system.module.requirement.dto.RequirementUpdateDTO;
import com.demand.system.module.requirement.dto.RequirementListVO;
import com.demand.system.module.requirement.dto.RequirementVO;
import com.demand.system.module.requirement.service.RequirementService;
import com.demand.system.module.requirement.service.RequirementApprovalEvaluationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/requirements")
public class RequirementController {

    private final RequirementService requirementService;
    private final RequirementApprovalEvaluationService approvalEvaluationService;

    public RequirementController(RequirementService requirementService,
                                 RequirementApprovalEvaluationService approvalEvaluationService) {
        this.requirementService = requirementService;
        this.approvalEvaluationService = approvalEvaluationService;
    }

    @GetMapping
    public Result<PageResult<RequirementListVO>> list(RequirementQueryDTO query) {
        return Result.success(requirementService.list(query));
    }

    /**
     * 导出需求数据为 Excel 文件
     * <p>按当前检索条件查询数据并生成 Excel 下载。
     * 使用 SXSSFWorkbook 流式写入，避免大数据量 OOM。
     * 支持通过 columns 参数指定导出列（逗号分隔的列 key），为空则导出全部列。
     * 必须在 @GetMapping("/{id}") 之前声明，否则 "export" 会被当作 id 参数匹配。
     *
     * @param query    检索条件（与列表查询共用 RequirementQueryDTO）
     * @param view     视图类型：all / drafts / pending / done / follows
     * @param columns  导出列 key（逗号分隔），如 "title,requirementNo,status"；为空则导出全部
     * @param response HTTP 响应
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('button:requirement:export')")
    public void exportExcel(RequirementQueryDTO query,
                            @RequestParam(defaultValue = "all") String view,
                            @RequestParam(required = false) String columns,
                            HttpServletResponse response) {
        try {
            // 设置响应头（先于写入，确保浏览器尽早弹出保存对话框）
            String fileName = URLEncoder.encode(
                    "需求列表_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx",
                    StandardCharsets.UTF_8
            );
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

            // 解析前端传来的列配置
            List<String> columnKeys = null;
            if (columns != null && !columns.isBlank()) {
                columnKeys = java.util.Arrays.stream(columns.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }

            // 流式生成 Excel 并直接写入 response OutputStream，避免全量加载到内存
            generateExcelStream(requirementService.listForExport(query, view), columnKeys, response.getOutputStream());
            response.getOutputStream().flush();
        } catch (IOException e) {
            try {
                response.reset();
                response.setStatus(500);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":500,\"message\":\"导出失败\"}");
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 全部可导出列定义（key → {中文表头, 列宽, 数据字段key}）。
     * 列宽单位为字符数，与前端 requirementAllColumns 保持一致。
     * 新增列时需同步更新此映射和前端 requirementAllColumns。
     */
    private static final java.util.LinkedHashMap<String, String[]> EXPORT_COLUMN_MAP = new java.util.LinkedHashMap<>() {{
        put("title",                   new String[]{"需求标题",     "30", "title"});
        put("requirementNo",           new String[]{"需求编号",     "22", "requirementNo"});
        put("type",                    new String[]{"类型",         "10", "typeName"});
        put("priority",                new String[]{"优先级",       "10", "priorityName"});
        put("status",                  new String[]{"状态",         "10", "status"});
        put("creatorName",             new String[]{"提出人",       "12", "creatorName"});
        put("assigneeName",            new String[]{"负责人",       "12", "assigneeName"});
        put("departmentName",          new String[]{"归属部门",     "12", "departmentName"});
        put("createdAt",               new String[]{"创建时间",     "20", "createdAt"});
        put("dueDate",                 new String[]{"期望上线日期", "16", "dueDate"});
        put("analysisCompletedAt",     new String[]{"分析完成时间", "20", "analysisCompletedAt"});
        put("confirmAt",               new String[]{"需求确认时间", "20", "confirmAt"});
        put("developmentCompletedAt",  new String[]{"开发完成时间", "20", "developmentCompletedAt"});
        put("description",             new String[]{"描述",         "40", "description"});
    }};

    /**
     * 根据列配置动态生成 Excel 表头定义。
     * @param columnKeys 前端传来的列 key 列表（按显示顺序），为空则使用全部列
     * @return columns 数组：每项为 {中文表头, 列宽, 数据字段key}
     */
    private String[][] resolveExportColumns(List<String> columnKeys) {
        if (columnKeys == null || columnKeys.isEmpty()) {
            // 未指定列：使用全部列（保持 EXPORT_COLUMN_MAP 的定义顺序）
            return EXPORT_COLUMN_MAP.values().toArray(new String[0][]);
        }
        // 按前端传来的 key 顺序映射，忽略无效 key
        java.util.List<String[]> resolved = new java.util.ArrayList<>();
        for (String key : columnKeys) {
            String[] col = EXPORT_COLUMN_MAP.get(key);
            if (col != null) {
                resolved.add(col);
            }
        }
        if (resolved.isEmpty()) {
            // 全部无效则降级为全部列
            return EXPORT_COLUMN_MAP.values().toArray(new String[0][]);
        }
        return resolved.toArray(new String[0][]);
    }

    /**
     * 使用 SXSSFWorkbook 流式写入 Excel，避免大数据量 OOM。
     * 数据行逐行写入后自动刷新到磁盘临时文件，内存中仅保留 windowSize 行。
     * @param dataList   数据行（Map<字段key, 值>）
     * @param columnKeys 前端传来的列 key 列表，决定导出哪些列及顺序；为空则导出全部
     * @param out        输出流
     */
    private void generateExcelStream(List<Map<String, Object>> dataList,
                                     List<String> columnKeys,
                                     java.io.OutputStream out) throws IOException {
        String[][] columns = resolveExportColumns(columnKeys);

        // SXSSF: 内存仅保留 100 行，超出的自动刷到磁盘临时文件
        try (var workbook = new org.apache.poi.xssf.streaming.SXSSFWorkbook(100)) {
            var sheet = workbook.createSheet("需求列表");

            // 表头样式
            var headerStyle = workbook.createCellStyle();
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.PALE_BLUE.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            // 数据样式
            var dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            dataStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            dataStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            dataStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            dataStyle.setWrapText(true);

            // 写入表头
            var headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(columns[i][0]);
                cell.setCellStyle(headerStyle);
            }

            // 设置列宽
            for (int i = 0; i < columns.length; i++) {
                sheet.setColumnWidth(i, Integer.parseInt(columns[i][1]) * 256);
            }

            // 写入数据行
            for (int rowIdx = 0; rowIdx < dataList.size(); rowIdx++) {
                var row = sheet.createRow(rowIdx + 1);
                var data = dataList.get(rowIdx);
                for (int colIdx = 0; colIdx < columns.length; colIdx++) {
                    var cell = row.createCell(colIdx);
                    Object value = data.get(columns[colIdx][2]);
                    cell.setCellValue(value != null ? String.valueOf(value) : "");
                    cell.setCellStyle(dataStyle);
                }
            }

            // 直接写入 response 输出流，无需中间 ByteArrayOutputStream
            workbook.write(out);
            // 清理临时文件
            workbook.dispose();
        }
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<Void> create(@Valid @RequestBody RequirementCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        requirementService.create(dto, userId);
        return Result.success();
    }

    @PostMapping("/drafts")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createDraft(@RequestBody RequirementDraftCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        return Result.success(requirementService.createDraft(dto, userId));
    }

    @PutMapping("/{id}/draft")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateDraft(@PathVariable Long id, @RequestBody RequirementDraftUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        dto.setId(id);
        requirementService.updateDraft(dto, userId);
        return Result.success();
    }

    @GetMapping("/my-drafts")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<RequirementVO>> listMyDrafts(RequirementMyListQueryDTO query) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        return Result.success(requirementService.listMyDrafts(query, userId));
    }

    @GetMapping("/my-pending")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<RequirementVO>> listMyPending(RequirementMyListQueryDTO query) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        return Result.success(requirementService.listMyPending(query, userId));
    }

    @GetMapping("/my-follows")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<RequirementVO>> listMyFollows(RequirementMyListQueryDTO query) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        return Result.success(requirementService.listMyFollows(query, userId));
    }

    @GetMapping("/my-done")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<RequirementVO>> listMyDone(RequirementMyListQueryDTO query) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        return Result.success(requirementService.listMyDone(query, userId));
    }

    @PostMapping("/{id}/follow")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> follow(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        requirementService.follow(id, userId);
        return Result.success();
    }

    @DeleteMapping("/{id}/follow")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> unfollow(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        requirementService.unfollow(id, userId);
        return Result.success();
    }

    @GetMapping("/{id}/next-nodes")
    @PreAuthorize("isAuthenticated()")
    public Result<List<NextNodeOptionDTO>> getNextNodes(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        return Result.success(requirementService.getNextNodes(id, userId));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public Result<RequirementVO> submit(@PathVariable Long id, @RequestBody RequirementSubmitDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        return Result.success(requirementService.submit(id, dto, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('button:requirement:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RequirementUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        dto.setId(id);
        requirementService.update(dto, userId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        requirementService.delete(id, userId);
        return Result.success();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> restore(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        requirementService.restore(id, userId);
        return Result.success();
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> getHistory(@PathVariable Long id) {
        return Result.success(requirementService.getHistory(id));
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("isAuthenticated()")
    public Result<List<RequirementCommentVO>> getComments(@PathVariable Long id) {
        return Result.success(requirementService.getComments(id));
    }

    @GetMapping("/{id}/approval-evaluations")
    @PreAuthorize("isAuthenticated()")
    public Result<List<RequirementApprovalEvaluationVO>> getApprovalEvaluations(@PathVariable Long id) {
        return Result.success(requirementService.getApprovalEvaluations(id));
    }

    @PostMapping("/{id}/approval-evaluations/{evaluationId}/supplements")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> addApprovalSupplement(@PathVariable Long id,
                                              @PathVariable Long evaluationId,
                                              @Valid @RequestBody RequirementApprovalSupplementCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        approvalEvaluationService.addSupplement(id, evaluationId, userId, dto.getContent(), dto.getAttachments());
        return Result.success();
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> addComment(@PathVariable Long id, @Valid @RequestBody RequirementCommentCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        requirementService.addComment(id, dto, userId);
        return Result.success();
    }

    @GetMapping("/{id}/children")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> getChildren(@PathVariable Long id) {
        return Result.success(requirementService.getChildren(id));
    }

    @GetMapping("/{id}/detail-batch")
    @PreAuthorize("isAuthenticated()")
    public Result<RequirementDetailVO> getDetailBatch(@PathVariable Long id) {
        return Result.success(requirementService.getDetailBatch(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<RequirementVO> getDetail(@PathVariable Long id) {
        return Result.success(requirementService.getDetail(id));
    }
}
