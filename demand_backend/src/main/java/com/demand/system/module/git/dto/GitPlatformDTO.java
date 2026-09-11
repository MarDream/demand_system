package com.demand.system.module.git.dto;

import jakarta.validation.constraints.NotBlank;

public class GitPlatformDTO {

    @NotBlank(message = "平台名称不能为空")
    private String name;

    @NotBlank(message = "平台类型不能为空")
    private String platformType;

    @NotBlank(message = "平台地址不能为空")
    private String baseUrl;

    private String authType;

    private String credential;

    private Integer isDefault;

    private String status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlatformType() {
        return platformType;
    }

    public void setPlatformType(String platformType) {
        this.platformType = platformType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getCredential() {
        return credential;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
