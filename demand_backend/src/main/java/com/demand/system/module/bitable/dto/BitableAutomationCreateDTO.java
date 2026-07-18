package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 多维表格-自动化规则创建DTO
 */
public class BitableAutomationCreateDTO {

    @NotBlank(message = "自动化名称不能为空")
    private String name;

    private Long tableId;

    @NotBlank(message = "触发器类型不能为空")
    private String triggerType;

    private Object triggerConfig;

    @NotBlank(message = "动作类型不能为空")
    private String actionType;

    private Object actionConfig;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public Object getTriggerConfig() {
        return triggerConfig;
    }

    public void setTriggerConfig(Object triggerConfig) {
        this.triggerConfig = triggerConfig;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Object getActionConfig() {
        return actionConfig;
    }

    public void setActionConfig(Object actionConfig) {
        this.actionConfig = actionConfig;
    }
}
