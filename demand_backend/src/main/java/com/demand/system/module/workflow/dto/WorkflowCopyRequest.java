package com.demand.system.module.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 工作流复制请求 DTO
 */
@Schema(description = "工作流复制请求")
public class WorkflowCopyRequest {

    @Schema(description = "新工作流名称", example = "审批流程 - 副本")
    @NotBlank(message = "工作流名称不能为空")
    @Size(max = 100, message = "工作流名称长度不能超过100个字符")
    private String newName;

    @Schema(description = "新版本号（可选，不填则自动生成）", example = "v2.0")
    @Size(max = 20, message = "版本号长度不能超过20个字符")
    private String newVersion;

    @Schema(description = "是否包含描述", example = "true")
    private Boolean includeDescription = true;

    @Schema(description = "是否包含节点配置", example = "true")
    private Boolean includeNodes = true;

    @Schema(description = "是否包含连线配置", example = "true")
    private Boolean includeEdges = true;

    @Schema(description = "是否重置审批人（清空审批人配置）", example = "false")
    private Boolean resetApprovers = false;

    @Schema(description = "是否重置表单字段（清空表单默认值）", example = "false")
    private Boolean resetFormFields = false;

    @Schema(description = "是否清空敏感数据（密钥、密码等）", example = "true")
    private Boolean resetSensitiveData = true;

    @Schema(description = "自定义敏感字段列表（用于额外脱敏）")
    private List<String> customSensitiveFields;

    @Schema(description = "目标项目ID（跨项目复制时使用）")
    private Long targetProjectId;

    // Getters and Setters

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    public String getNewVersion() {
        return newVersion;
    }

    public void setNewVersion(String newVersion) {
        this.newVersion = newVersion;
    }

    public Boolean getIncludeDescription() {
        return includeDescription;
    }

    public void setIncludeDescription(Boolean includeDescription) {
        this.includeDescription = includeDescription;
    }

    public Boolean getIncludeNodes() {
        return includeNodes;
    }

    public void setIncludeNodes(Boolean includeNodes) {
        this.includeNodes = includeNodes;
    }

    public Boolean getIncludeEdges() {
        return includeEdges;
    }

    public void setIncludeEdges(Boolean includeEdges) {
        this.includeEdges = includeEdges;
    }

    public Boolean getResetApprovers() {
        return resetApprovers;
    }

    public void setResetApprovers(Boolean resetApprovers) {
        this.resetApprovers = resetApprovers;
    }

    public Boolean getResetFormFields() {
        return resetFormFields;
    }

    public void setResetFormFields(Boolean resetFormFields) {
        this.resetFormFields = resetFormFields;
    }

    public Boolean getResetSensitiveData() {
        return resetSensitiveData;
    }

    public void setResetSensitiveData(Boolean resetSensitiveData) {
        this.resetSensitiveData = resetSensitiveData;
    }

    public List<String> getCustomSensitiveFields() {
        return customSensitiveFields;
    }

    public void setCustomSensitiveFields(List<String> customSensitiveFields) {
        this.customSensitiveFields = customSensitiveFields;
    }

    public Long getTargetProjectId() {
        return targetProjectId;
    }

    public void setTargetProjectId(Long targetProjectId) {
        this.targetProjectId = targetProjectId;
    }
}
