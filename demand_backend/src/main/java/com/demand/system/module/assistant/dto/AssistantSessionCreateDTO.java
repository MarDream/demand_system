package com.demand.system.module.assistant.dto;

import jakarta.validation.constraints.Size;

public class AssistantSessionCreateDTO {

    @Size(max = 120, message = "会话标题长度不能超过 120")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
