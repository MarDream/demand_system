package com.demand.system.module.bitable.dto;

/**
 * 多维表格-自动化规则更新DTO
 */
public class BitableAutomationUpdateDTO {

    private String name;

    private String status;

    private Object triggerConfig;

    private Object actionConfig;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Object getTriggerConfig() {
        return triggerConfig;
    }

    public void setTriggerConfig(Object triggerConfig) {
        this.triggerConfig = triggerConfig;
    }

    public Object getActionConfig() {
        return actionConfig;
    }

    public void setActionConfig(Object actionConfig) {
        this.actionConfig = actionConfig;
    }
}
