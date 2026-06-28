package com.demand.system.module.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class WorkflowVersionMetaUpdateDTO {

    @NotBlank(message = "版本号不能为空")
    @Pattern(regexp = "^[1-9]\\d*(?:\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*))?$", message = "版本号格式需为正整数或 1.0.0")
    @Size(max = 20, message = "版本号不能超过20个字符")
    private String version;

    @NotBlank(message = "版本名称不能为空")
    @Size(max = 50, message = "版本名称不能超过50个字符")
    private String name;

    private Long knowledgeBaseId;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }
}
