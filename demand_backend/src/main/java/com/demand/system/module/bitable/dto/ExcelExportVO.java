package com.demand.system.module.bitable.dto;

/**
 * 导出文件信息VO
 */
public class ExcelExportVO {

    private String fileName;

    private byte[] fileData;

    public ExcelExportVO() {
    }

    public ExcelExportVO(String fileName, byte[] fileData) {
        this.fileName = fileName;
        this.fileData = fileData;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
    }
}