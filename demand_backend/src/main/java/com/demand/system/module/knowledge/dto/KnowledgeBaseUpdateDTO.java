package com.demand.system.module.knowledge.dto;

import jakarta.validation.constraints.Size;

public class KnowledgeBaseUpdateDTO {

    @Size(max = 200, message = "名称最长200字")
    private String name;

    @Size(max = 500, message = "描述最长500字")
    private String description;

    /** 文档处理超时时间(分钟), 0=不超时 */
    private Integer docTimeoutMinutes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDocTimeoutMinutes() {
        return docTimeoutMinutes;
    }

    public void setDocTimeoutMinutes(Integer docTimeoutMinutes) {
        this.docTimeoutMinutes = docTimeoutMinutes;
    }
}
