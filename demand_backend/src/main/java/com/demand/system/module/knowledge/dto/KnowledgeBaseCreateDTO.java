package com.demand.system.module.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeBaseCreateDTO {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 200, message = "名称最长200字")
    private String name;

    @Size(max = 500, message = "描述最长500字")
    private String description;

    private Long projectId;
}
