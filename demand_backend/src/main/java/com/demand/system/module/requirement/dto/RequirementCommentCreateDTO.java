package com.demand.system.module.requirement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class RequirementCommentCreateDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 10000, message = "评论内容不能超过10000个字符")
    private String content;

    /** 被@的用户ID列表（编辑器提取，最多50人；后端会结合正文兜底解析并校验） */
    @Size(max = 50, message = "一次最多@50位用户")
    private List<Long> mentionedUserIds;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Long> getMentionedUserIds() {
        return mentionedUserIds;
    }

    public void setMentionedUserIds(List<Long> mentionedUserIds) {
        this.mentionedUserIds = mentionedUserIds;
    }
}
