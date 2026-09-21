package com.demand.system.module.bitable.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 多维表格-角色字段权限实体。
 * <p>
 * 记录「某个角色在某张表的某个字段上」的可见性级别：
 * {@code editable}（可编辑，默认）/ {@code readonly}（仅可见，不可改）/ {@code hidden}（不可见）。
 * <p>
 * {@code role_identifier} 是数据库生成的列（系统角色为 {@code system:owner} 形式，
 * 自定义角色为 {@code custom:<id>}），用于配合 {@code (base_id, role_identifier, field_id)} 唯一键做 upsert。
 * 该列由数据库生成，Java 侧只读，禁止写入。
 */
@TableName("bitable_field_permissions")
public class BitableFieldPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long baseId;

    private String roleType;

    private String systemRoleCode;

    private Long customRoleId;

    private Long tableId;

    private Long fieldId;

    private String permissionLevel;

    /** 选项级权限配置 JSON: {mode:all|partial, editableKeys:[选项label], manage:full|add-only}；null=未配置 */
    private String optionConfig;

    private Long creatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBaseId() { return baseId; }
    public void setBaseId(Long baseId) { this.baseId = baseId; }

    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }

    public String getSystemRoleCode() { return systemRoleCode; }
    public void setSystemRoleCode(String systemRoleCode) { this.systemRoleCode = systemRoleCode; }

    public Long getCustomRoleId() { return customRoleId; }
    public void setCustomRoleId(Long customRoleId) { this.customRoleId = customRoleId; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public Long getFieldId() { return fieldId; }
    public void setFieldId(Long fieldId) { this.fieldId = fieldId; }

    public String getPermissionLevel() { return permissionLevel; }
    public void setPermissionLevel(String permissionLevel) { this.permissionLevel = permissionLevel; }

    public String getOptionConfig() { return optionConfig; }
    public void setOptionConfig(String optionConfig) { this.optionConfig = optionConfig; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BitableFieldPermission that = (BitableFieldPermission) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
