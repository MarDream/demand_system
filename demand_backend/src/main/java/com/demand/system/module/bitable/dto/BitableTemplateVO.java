package com.demand.system.module.bitable.dto;

/**
 * 模板视图对象
 */
public class BitableTemplateVO {

    private String code;

    private String name;

    private String description;

    private String icon;

    private Integer fieldCount;

    public BitableTemplateVO() {
    }

    public BitableTemplateVO(String code, String name, String description, String icon, Integer fieldCount) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.fieldCount = fieldCount;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getFieldCount() {
        return fieldCount;
    }

    public void setFieldCount(Integer fieldCount) {
        this.fieldCount = fieldCount;
    }
}