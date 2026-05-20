package com.demand.system.module.knowledge.dto;

import java.time.LocalDateTime;

public class KnowledgeDocumentVO {

    private Long id;
    private Long knowledgeBaseId;
    private Long projectId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer chunkCount;
    private String status;
    private String errorMessage;
    private Long requirementId;
    private String sourceType;
    private Long sourceId;
    private Long uploaderId;
    private String uploaderName;
    private String projectName;
    private Integer downloadCount;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }

    public String getUploaderName() {
        return uploaderName;
    }

    public void setUploaderName(String uploaderName) {
        this.uploaderName = uploaderName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private Long knowledgeBaseId;
        private Long projectId;
        private String fileName;
        private String fileType;
        private Long fileSize;
        private Integer chunkCount;
        private String status;
        private String errorMessage;
        private Long requirementId;
        private String sourceType;
        private Long sourceId;
        private Long uploaderId;
        private String uploaderName;
        private String projectName;
        private Integer downloadCount;
        private LocalDateTime createdAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder knowledgeBaseId(Long knowledgeBaseId) {
            this.knowledgeBaseId = knowledgeBaseId;
            return this;
        }

        public Builder projectId(Long projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder fileType(String fileType) {
            this.fileType = fileType;
            return this;
        }

        public Builder fileSize(Long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public Builder chunkCount(Integer chunkCount) {
            this.chunkCount = chunkCount;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder requirementId(Long requirementId) {
            this.requirementId = requirementId;
            return this;
        }

        public Builder sourceType(String sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder sourceId(Long sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public Builder uploaderId(Long uploaderId) {
            this.uploaderId = uploaderId;
            return this;
        }

        public Builder uploaderName(String uploaderName) {
            this.uploaderName = uploaderName;
            return this;
        }

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder downloadCount(Integer downloadCount) {
            this.downloadCount = downloadCount;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public KnowledgeDocumentVO build() {
            KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
            vo.setId(id);
            vo.setKnowledgeBaseId(knowledgeBaseId);
            vo.setProjectId(projectId);
            vo.setFileName(fileName);
            vo.setFileType(fileType);
            vo.setFileSize(fileSize);
            vo.setChunkCount(chunkCount);
            vo.setStatus(status);
            vo.setErrorMessage(errorMessage);
            vo.setRequirementId(requirementId);
            vo.setSourceType(sourceType);
            vo.setSourceId(sourceId);
            vo.setUploaderId(uploaderId);
            vo.setUploaderName(uploaderName);
            vo.setProjectName(projectName);
            vo.setDownloadCount(downloadCount);
            vo.setCreatedAt(createdAt);
            return vo;
        }
    }
}
