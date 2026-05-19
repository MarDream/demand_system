package com.demand.system.module.knowledge.dto;

import java.time.LocalDateTime;

public class KnowledgePublicShareContextVO {

    private String shareToken;
    private String accessToken;
    private Long knowledgeBaseId;
    private Long documentId;
    private String fileName;
    private String fileType;
    private LocalDateTime expireAt;
    private Boolean requireLogin;
    private Boolean oneTimeAccess;
    private String previewUrl;

    public String getShareToken() {
        return shareToken;
    }

    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
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

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public Boolean getRequireLogin() {
        return requireLogin;
    }

    public void setRequireLogin(Boolean requireLogin) {
        this.requireLogin = requireLogin;
    }

    public Boolean getOneTimeAccess() {
        return oneTimeAccess;
    }

    public void setOneTimeAccess(Boolean oneTimeAccess) {
        this.oneTimeAccess = oneTimeAccess;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String shareToken;
        private String accessToken;
        private Long knowledgeBaseId;
        private Long documentId;
        private String fileName;
        private String fileType;
        private LocalDateTime expireAt;
        private Boolean requireLogin;
        private Boolean oneTimeAccess;
        private String previewUrl;

        public Builder shareToken(String shareToken) {
            this.shareToken = shareToken;
            return this;
        }

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder knowledgeBaseId(Long knowledgeBaseId) {
            this.knowledgeBaseId = knowledgeBaseId;
            return this;
        }

        public Builder documentId(Long documentId) {
            this.documentId = documentId;
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

        public Builder expireAt(LocalDateTime expireAt) {
            this.expireAt = expireAt;
            return this;
        }

        public Builder requireLogin(Boolean requireLogin) {
            this.requireLogin = requireLogin;
            return this;
        }

        public Builder oneTimeAccess(Boolean oneTimeAccess) {
            this.oneTimeAccess = oneTimeAccess;
            return this;
        }

        public Builder previewUrl(String previewUrl) {
            this.previewUrl = previewUrl;
            return this;
        }

        public KnowledgePublicShareContextVO build() {
            KnowledgePublicShareContextVO vo = new KnowledgePublicShareContextVO();
            vo.setShareToken(shareToken);
            vo.setAccessToken(accessToken);
            vo.setKnowledgeBaseId(knowledgeBaseId);
            vo.setDocumentId(documentId);
            vo.setFileName(fileName);
            vo.setFileType(fileType);
            vo.setExpireAt(expireAt);
            vo.setRequireLogin(requireLogin);
            vo.setOneTimeAccess(oneTimeAccess);
            vo.setPreviewUrl(previewUrl);
            return vo;
        }
    }
}
