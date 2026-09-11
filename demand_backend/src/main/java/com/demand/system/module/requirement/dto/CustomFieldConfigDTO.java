package com.demand.system.module.requirement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 需求动态字段 schema（字段定义 + 运行时权限 + 当前值）。
 * <p>同时承载配置管理和运行时表单渲染，权限集合由授权服务按需求类型/工作流节点计算。
 */
public class CustomFieldConfigDTO {

    private Long id;

    /** 稳定字段编码，不可变。 */
    private String fieldCode;

    /** 所属需求类型编码（为空表示项目级全局字段）。 */
    private String requirementTypeCode;

    /** 展示名称，可改。 */
    private String name;

    /** TEXT / SELECT / DATE / NUMBER / MULTI_SELECT / USER / MULTI_USER / BOOLEAN / URL / FILE。 */
    private String fieldType;

    /** 单选/多选选项 JSON 数组。 */
    private String options;

    /** 选项结构化列表（key=稳定编码存值，label=展示名），由后端从 options 解析。 */
    private List<FieldOption> optionList;

    /** 是否必填。 */
    private Boolean required;

    /** 默认值（文本/选项字符串；数值/日期/布尔由 defaultValue 附带类型标记）。 */
    private String defaultValue;

    private Integer sortOrder;

    private Boolean enabled;

    // ---- 运行时权限 ----
    private Boolean visible;

    private Boolean editable;

    // ---- 当前值 ----
    private String value;

    private BigDecimal valueNumber;

    private LocalDate valueDate;

    private Boolean valueBoolean;

    private Long valueUserId;

    private List<Object> values;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public String getRequirementTypeCode() {
        return requirementTypeCode;
    }

    public void setRequirementTypeCode(String requirementTypeCode) {
        this.requirementTypeCode = requirementTypeCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public List<FieldOption> getOptionList() {
        return optionList;
    }

    public void setOptionList(List<FieldOption> optionList) {
        this.optionList = optionList;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    public Boolean getEditable() {
        return editable;
    }

    public void setEditable(Boolean editable) {
        this.editable = editable;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public BigDecimal getValueNumber() {
        return valueNumber;
    }

    public void setValueNumber(BigDecimal valueNumber) {
        this.valueNumber = valueNumber;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public Boolean getValueBoolean() {
        return valueBoolean;
    }

    public void setValueBoolean(Boolean valueBoolean) {
        this.valueBoolean = valueBoolean;
    }

    public Long getValueUserId() {
        return valueUserId;
    }

    public void setValueUserId(Long valueUserId) {
        this.valueUserId = valueUserId;
    }

    public List<Object> getValues() {
        return values;
    }

    public void setValues(List<Object> values) {
        this.values = values;
    }
}