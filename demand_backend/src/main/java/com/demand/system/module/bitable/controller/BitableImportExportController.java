package com.demand.system.module.bitable.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.entity.BitableTable;
import com.demand.system.module.bitable.mapper.BitableTableMapper;
import com.demand.system.module.bitable.service.BitableImportExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 多维表格导入导出 Controller
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableImportExportController {

    private final BitableImportExportService importExportService;
    private final BitableTableMapper tableMapper;

    public BitableImportExportController(BitableImportExportService importExportService,
                                           BitableTableMapper tableMapper) {
        this.importExportService = importExportService;
        this.tableMapper = tableMapper;
    }

    /**
     * 导出数据表为 Excel
     */
    @GetMapping("/tables/{tableId}/export/excel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Long tableId) {
        BitableTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException("数据表不存在");
        }

        byte[] data = importExportService.exportTableToExcel(tableId);
        String fileName = table.getName() + ".xlsx";

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
        BitableTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException("数据表不存在");
        }

        byte[] data = importExportService.exportTableToCsv(tableId);
        String fileName = table.getName() + ".csv";

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
        try {
            List<Long> ids = importExportService.importFromExcel(tableId, file.getBytes(), userId);
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
        try {
            String csvContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<Long> ids = importExportService.importFromCsv(tableId, csvContent, userId);
            return Result.success(ids);
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败: " + e.getMessage());
        }
    }
}