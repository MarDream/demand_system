package com.demand.system.module.requirement.dto;

import lombok.Data;

@Data
public class RequirementAttachmentDTO {

    private Long fileId;

    private String name;

    private String url;

    private Long size;

    private String contentType;

    private String bucketName;

    private String objectName;
}
