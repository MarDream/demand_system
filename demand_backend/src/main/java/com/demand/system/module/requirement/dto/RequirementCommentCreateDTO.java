package com.demand.system.module.requirement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RequirementCommentCreateDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 10000, message = "评论内容不能超过10000个字符")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
