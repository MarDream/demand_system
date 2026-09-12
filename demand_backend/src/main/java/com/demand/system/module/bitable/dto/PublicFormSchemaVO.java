package com.demand.system.module.bitable.dto;

import java.util.List;

/**
 * 公开表单脱敏 schema VO（匿名访问）。
 * 不暴露表ID/Base ID/隐藏字段/内部字段配置（AI 提示词、公式等）。
 */
public class PublicFormSchemaVO {

    private String title;

    private String description;

    private Boolean hasPassword;

    private String successMessage;

    private String redirectUrl;

    private List<PublicFormField> fields;

    public static class PublicFormField {
        private Long id;
        private String name;
        private String fieldType;
        private Boolean required;
        private String description;
        /** 单选/多选等选项：label + color */
        private Object options;
        /** 表单占位提示 */
        private String placeholder;

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

        public String getFieldType() {
            return fieldType;
        }

        public void setFieldType(String fieldType) {
            this.fieldType = fieldType;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Object getOptions() {
            return options;
        }

        public void setOptions(Object options) {
            this.options = options;
        }

        public String getPlaceholder() {
            return placeholder;
        }

        public void setPlaceholder(String placeholder) {
            this.placeholder = placeholder;
        }
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getHasPassword() {
        return hasPassword;
    }

    public void setHasPassword(Boolean hasPassword) {
        this.hasPassword = hasPassword;
    }

    public String getSuccessMessage() {
        return successMessage;
    }

    public void setSuccessMessage(String successMessage) {
        this.successMessage = successMessage;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public List<PublicFormField> getFields() {
        return fields;
    }

    public void setFields(List<PublicFormField> fields) {
        this.fields = fields;
    }
}
