package com.demand.system.module.requirement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class RequirementTemplateSaveDTO {

    @NotBlank(message = "需求类型不能为空")
    private String requirementTypeCode;

    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    @NotNull(message = "模板内容不能为空")
    private Map<String, Object> templateContent;

    public String getRequirementTypeCode() {
        return requirementTypeCode;
    }

    public void setRequirementTypeCode(String requirementTypeCode) {
        this.requirementTypeCode = requirementTypeCode;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public Map<String, Object> getTemplateContent() {
        return templateContent;
    }

    public void setTemplateContent(Map<String, Object> templateContent) {
        this.templateContent = templateContent;
    }
}
