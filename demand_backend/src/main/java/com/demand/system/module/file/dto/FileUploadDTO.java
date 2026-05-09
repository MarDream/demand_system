package com.demand.system.module.file.dto;

import lombok.Data;

@Data
public class FileUploadDTO {

    private Long fileId;

    private String name;

    private String url;

    private Long size;

    private String contentType;

    private String bucketName;

    private String objectName;
}
