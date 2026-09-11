package com.demand.system.module.git.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class GitRepositoryDTO {

    @NotNull(message = "所属平台不能为空")
    private Long platformId;

    private Long projectId;

    @NotBlank(message = "仓库名称不能为空")
    private String name;

    private String fullPath;

    private String description;

    private String defaultBranch;

    private String cloneUrlSsh;

    private String cloneUrlHttps;

    private String remoteId;

    private String status;

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getCloneUrlSsh() {
        return cloneUrlSsh;
    }

    public void setCloneUrlSsh(String cloneUrlSsh) {
        this.cloneUrlSsh = cloneUrlSsh;
    }

    public String getCloneUrlHttps() {
        return cloneUrlHttps;
    }

    public void setCloneUrlHttps(String cloneUrlHttps) {
        this.cloneUrlHttps = cloneUrlHttps;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
