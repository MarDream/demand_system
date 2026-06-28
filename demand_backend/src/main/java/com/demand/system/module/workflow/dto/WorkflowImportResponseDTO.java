package com.demand.system.module.workflow.dto;

/**
 * 工作流导入响应结构
 */
public class WorkflowImportResponseDTO {

    private Boolean success;
    private Long versionId;
    private String version;
    private String name;
    private String message;
    private ConflictInfo conflicts;

    public static class ConflictInfo {
        private Boolean nameConflict;
        private Boolean versionConflict;
        private String resolvedName;
        private String resolvedVersion;

        public Boolean getNameConflict() {
            return nameConflict;
        }

        public void setNameConflict(Boolean nameConflict) {
            this.nameConflict = nameConflict;
        }

        public Boolean getVersionConflict() {
            return versionConflict;
        }

        public void setVersionConflict(Boolean versionConflict) {
            this.versionConflict = versionConflict;
        }

        public String getResolvedName() {
            return resolvedName;
        }

        public void setResolvedName(String resolvedName) {
            this.resolvedName = resolvedName;
        }

        public String getResolvedVersion() {
            return resolvedVersion;
        }

        public void setResolvedVersion(String resolvedVersion) {
            this.resolvedVersion = resolvedVersion;
        }
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ConflictInfo getConflicts() {
        return conflicts;
    }

    public void setConflicts(ConflictInfo conflicts) {
        this.conflicts = conflicts;
    }
}
