package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 字段权限配置项：某角色在某张表的某个字段上的可见性级别。
 * <p>
 * {@code permissionLevel} 取值：{@code editable}（可编辑）/ {@code readonly}（只读）/ {@code hidden}（隐藏）。
 * 缺省即 {@code editable}，因此「保存时不下发」等同于恢复默认。
 */
public class BitableFieldPermissionDTO {

    private Long baseId;

    @NotBlank(message = "角色类型不能为空")
    private String roleType;

    private String systemRoleCode;

    private Long customRoleId;

    @NotNull(message = "数据表ID不能为空")
    private Long tableId;

    @NotNull(message = "字段ID不能为空")
    private Long fieldId;

    @NotBlank(message = "权限级别不能为空")
    private String permissionLevel;

    /** 选项级权限配置 JSON 字符串；null=未配置（前端序列化/解析） */
    private String optionConfig;

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
}
