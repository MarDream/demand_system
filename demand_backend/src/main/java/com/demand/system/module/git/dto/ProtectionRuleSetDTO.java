package com.demand.system.module.git.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class ProtectionRuleSetDTO {

    private Long id;

    @NotBlank(message = "规则集名称不能为空")
    private String name;

    private String description;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
