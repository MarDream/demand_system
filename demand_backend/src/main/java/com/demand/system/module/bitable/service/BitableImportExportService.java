package com.demand.system.module.bitable.service;

import java.util.List;

/**
 * 多维表格导入导出 Service
 */
public interface BitableImportExportService {

    /**
     * 导出数据表为 Excel
     *
     * @param tableId 数据表ID
     * @return Excel 文件字节数组
     */
    byte[] exportTableToExcel(Long tableId);

    /**
     * 导出数据表为 CSV
     *
     * @param tableId 数据表ID
     * @return CSV 文件字节数组
     */
    byte[] exportTableToCsv(Long tableId);

    /**
     * 从 Excel 导入记录到数据表
     *
     * @param tableId   数据表ID
     * @param fileBytes Excel 文件字节数组
     * @param userId    操作人ID
     * @return 新建记录ID列表
     */
    List<Long> importFromExcel(Long tableId, byte[] fileBytes, Long userId);

    /**
     * 从 CSV 导入记录到数据表
     *
     * @param tableId    数据表ID
     * @param csvContent CSV 文本内容
     * @param userId     操作人ID
     * @return 新建记录ID列表
     */
    List<Long> importFromCsv(Long tableId, String csvContent, Long userId);
}