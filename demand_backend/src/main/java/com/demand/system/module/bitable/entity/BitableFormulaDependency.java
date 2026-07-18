package com.demand.system.module.bitable.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 多维表格-公式依赖关系实体
 */
@TableName("bitable_formula_dependencies")
public class BitableFormulaDependency {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long formulaFieldId;

    private Long dependencyFieldId;

    private String dependencyKind;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFormulaFieldId() {
        return formulaFieldId;
    }

    public void setFormulaFieldId(Long formulaFieldId) {
        this.formulaFieldId = formulaFieldId;
    }

    public Long getDependencyFieldId() {
        return dependencyFieldId;
    }

    public void setDependencyFieldId(Long dependencyFieldId) {
        this.dependencyFieldId = dependencyFieldId;
    }

    public String getDependencyKind() {
        return dependencyKind;
    }

    public void setDependencyKind(String dependencyKind) {
        this.dependencyKind = dependencyKind;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
