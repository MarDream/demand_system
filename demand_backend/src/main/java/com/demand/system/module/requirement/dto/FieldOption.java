package com.demand.system.module.requirement.dto;

/**
 * 动态字段选项。
 * <p>key 为稳定编码（字段值存储用它），label 为展示名（可随时修改，不影响历史数据）。
 */
public class FieldOption {

    /** 稳定选项编码，创建后不可变。 */
    private String key;

    /** 展示名称，可修改。 */
    private String label;

    public FieldOption() {
    }

    public FieldOption(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
