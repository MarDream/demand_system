package com.demand.system.module.preview.dto;

/**
 * 异步预览结果 VO。
 *
 * <p>用于轮询式异步预览场景：
 * <ul>
 *   <li>status=processing: 转换中，previewUrl 为空，前端需继续轮询</li>
 *   <li>status=completed: 转换完成，previewUrl 为可直接嵌入的最终 URL</li>
 *   <li>status=failed: 转换失败，message 包含错误原因</li>
 * </ul>
 */
public class AsyncPreviewVO {

    /** 转换状态: processing / completed / failed */
    private String status;

    /**
     * 预览服务访问 URL（用于前端嵌入 iframe）。
     *
     * <p>由后端在 submit 阶段生成，等转码完成后浏览器打开该 URL 即可看到内容。
     * 状态轮询响应中不携带该字段（前端应复用 submit 阶段拿到的 previewUrl）。</p>
     */
    private String previewUrl;

    /** 任务 ID（用于后续查询） */
    private String taskId;

    /** 状态描述或错误信息 */
    private String message;

    /** 转换进度（0-100），仅 processing/completed 场景有意义 */
    private Integer progress;

    /** 已等待秒数 */
    private Long waitingSeconds;

    public AsyncPreviewVO() {
    }

    public AsyncPreviewVO(String status, String previewUrl, String taskId, String message) {
        this(status, previewUrl, taskId, message, null, null);
    }

    public AsyncPreviewVO(String status, String previewUrl, String taskId, String message, Integer progress, Long waitingSeconds) {
        this.status = status;
        this.previewUrl = previewUrl;
        this.taskId = taskId;
        this.message = message;
        this.progress = progress;
        this.waitingSeconds = waitingSeconds;
    }

    public static AsyncPreviewVO processing(String taskId) {
        return new AsyncPreviewVO("processing", null, taskId, "文件转换中，请稍候...", 0, 0L);
    }

    /**
     * 处理中（已签发 previewUrl，可在前端缓存待转码完成后使用）。
     */
    public static AsyncPreviewVO processing(String taskId, String previewUrl) {
        return new AsyncPreviewVO("processing", previewUrl, taskId, "文件转换中，请稍候...", 0, 0L);
    }

    public static AsyncPreviewVO processing(String taskId, String previewUrl, String message, Integer progress, Long waitingSeconds) {
        return new AsyncPreviewVO("processing", previewUrl, taskId, message, progress, waitingSeconds);
    }

    public static AsyncPreviewVO completed(String previewUrl) {
        return new AsyncPreviewVO("completed", previewUrl, null, null, 100, 0L);
    }

    public static AsyncPreviewVO failed(String message) {
        return new AsyncPreviewVO("failed", null, null, message, 0, 0L);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Long getWaitingSeconds() {
        return waitingSeconds;
    }

    public void setWaitingSeconds(Long waitingSeconds) {
        this.waitingSeconds = waitingSeconds;
    }
}
