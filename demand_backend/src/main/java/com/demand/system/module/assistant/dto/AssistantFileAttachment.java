package com.demand.system.module.assistant.dto;

/**
 * 操作助手中用户上传的文件附件信息。
 * <p>前端通过 /api/v1/files/upload 上传后，将文件元信息连同"客户端读取的文字内容"
 * 一并传入对话请求。后端将其注入 LLM 提示词作为上下文。</p>
 */
public class AssistantFileAttachment {

    /** 文件记录 ID（已通过文件服务上传） */
    private Long fileId;

    /** 原始文件名 */
    private String name;

    /** 文件大小（字节） */
    private Long size;

    /** MIME 类型，如 text/plain, image/png, application/pdf */
    private String contentType;

    /**
     * 客户端从文本文件中读取的内容（utf-8）。
     * 图片/PDF/Office 等非文本文件此项为空或 null。
     */
    private String extractedText;

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }
}
