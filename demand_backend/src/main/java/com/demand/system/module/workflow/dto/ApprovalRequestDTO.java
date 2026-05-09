package com.demand.system.module.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApprovalRequestDTO {

    @NotBlank(message = "审核意见不能为空")
    private String comment;
}
