package com.demand.system.module.requirement.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Objects;

@TableName(value = "requirement_types", autoResultMap = true)
public class RequirementTypeConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String color;

    private Integer sortOrder;

    private Boolean isDefault;

    /**
     * 是否启用：false=禁用（不可用于新建需求），true=启用。
     * <p>工作流被禁用时由 {@code WorkflowActivationServiceImpl.deactivate} 联动置 false。
     * <p>手动开启时需校验绑定的工作流版本仍处于活跃状态（is_active=1 AND activation_status='active'）。
     */
    private Boolean enabled;

    /**
     * 绑定的工作流版本ID。
     * <p>取值来源：{@code workflow_versions.id}。运行时由 {@code WorkflowVersionResolver.resolveForType(code)}
     * 通过该字段解析工作流，必须指向 {@code is_active=1 AND activation_status='active'} 的版本。
     * <p>NULL 表示该需求类型未配置工作流，新建需求时该类型不出现，详情页不渲染"待办"操作按钮。
     */
    private Long workflowVersionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getWorkflowVersionId() {
        return workflowVersionId;
    }

    public void setWorkflowVersionId(Long workflowVersionId) {
        this.workflowVersionId = workflowVersionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequirementTypeConfig that = (RequirementTypeConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
