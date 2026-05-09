package com.demand.system.module.llm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("llm_providers")
public class LlmProvider {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String protocol;
    private String baseUrl;
    private String apiKey;
    private Boolean enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
