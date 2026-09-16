package com.demand.system.module.bitable.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.constant.OperationType;
import com.demand.system.module.bitable.dto.BitableTableVO;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableImportExportService;
import com.demand.system.module.bitable.service.BitableTableService;
import com.demand.system.module.bitable.util.BitableAuditHelper;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多维表格导入导出 Controller
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableImportExportController {

    private final BitableImportExportService importExportService;
    private final BitableTableService tableService;
    private final BitableAuthorizationService authorizationService;
    private final BitableAuditHelper auditHelper;

    public BitableImportExportController(BitableImportExportService importExportService,
                                         BitableTableService tableService,
                                         BitableAuthorizationService authorizationService,
                                         BitableAuditHelper auditHelper) {
        this.importExportService = importExportService;
        this.tableService = tableService;
        this.authorizationService = authorizationService;
        this.auditHelper = auditHelper;
    }

    /**
     * 导出数据表为 Excel
     */
    @GetMapping("/tables/{tableId}/export/excel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Long tableId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        authorizationService.checkReadPermission(baseId, userId);
        BitableTableVO table = tableService.getTableById(tableId);

        byte[] data = importExportService.exportTableToExcel(tableId);
        String fileName = table.getName() + ".xlsx";
        recordExportAudit(baseId, tableId, userId, table.getName(), "excel", data.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    /**
     * 导出数据表为 CSV
     */
    @GetMapping("/tables/{tableId}/export/csv")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long tableId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        authorizationService.checkReadPermission(baseId, userId);
        BitableTableVO table = tableService.getTableById(tableId);

        byte[] data = importExportService.exportTableToCsv(tableId);
        String fileName = table.getName() + ".csv";
        recordExportAudit(baseId, tableId, userId, table.getName(), "csv", data.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }

    /**
     * 从 Excel 导入记录
     */
    @PostMapping("/tables/{tableId}/import/excel")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Long>> importExcel(@PathVariable Long tableId,
                                           @RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        authorizationService.checkWritePermission(baseId, userId);
        try {
            List<Long> ids = importExportService.importFromExcel(tableId, file.getBytes(), userId);
            recordImportAudit(baseId, tableId, userId, "excel", ids == null ? 0 : ids.size());
            return Result.success(ids);
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 从 CSV 导入记录
     */
    @PostMapping("/tables/{tableId}/import/csv")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Long>> importCsv(@PathVariable Long tableId,
                                         @RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        authorizationService.checkWritePermission(baseId, userId);
        try {
            String csvContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<Long> ids = importExportService.importFromCsv(tableId, csvContent, userId);
            recordImportAudit(baseId, tableId, userId, "csv", ids == null ? 0 : ids.size());
            return Result.success(ids);
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败: " + e.getMessage());
        }
    }

    private void recordExportAudit(Long baseId, Long tableId, Long userId, String tableName, String format, int bytes) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tableId", tableId);
        detail.put("tableName", tableName);
        detail.put("format", format);
        detail.put("bytes", bytes);
        auditHelper.record(baseId, tableId, userId, OperationType.EXPORT_RECORDS, BitableJsonUtils.toJsonString(detail));
    }

    private void recordImportAudit(Long baseId, Long tableId, Long userId, String format, int count) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tableId", tableId);
        detail.put("format", format);
        detail.put("count", count);
        auditHelper.record(baseId, tableId, userId, OperationType.IMPORT_RECORDS, BitableJsonUtils.toJsonString(detail));
    }
}
