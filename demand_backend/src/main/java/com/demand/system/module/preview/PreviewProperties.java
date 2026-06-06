package com.demand.system.module.preview;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部文件预览服务配置。
 *
 * <p>封装第三方文件预览服务（如 kkFileView）的接入参数，
 * 业务代码不直接引用第三方服务名称，便于未来切换实现。</p>
 */
@Component
@ConfigurationProperties(prefix = "kkfileview")
public class PreviewProperties {

    /** 预览服务基础地址，如 http://kkfileview-host:8012 */
    private String baseUrl = "http://localhost:8012";

    /** 同步预览接口路径，默认 /onlinePreview */
    private String previewPath = "/onlinePreview";

    /**
     * 异步预览任务提交路径（kkFileView 异步端点），默认 /officeSubmit。
     *
     * <p>该端点不会同步阻塞等 kkFileView 转码完成，而是立即返回 taskId；
     * 调用方拿到 taskId 后通过 {@link #statusPath} 轮询状态。
     * 该改动解决了大文件预览时前端 axios 15s 超时问题。</p>
     */
    private String asyncPreviewPath = "/officeSubmit";

    /**
     * 异步预览状态查询路径（kkFileView 异步端点），默认 /getOfficeOnlineHtmlUrl。
     */
    private String statusPath = "/getOfficeOnlineHtmlUrl";

    /** 异步轮询最大次数，默认 30 次 */
    private int maxPollAttempts = 30;

    /** 异步轮询间隔（毫秒），默认 2000ms */
    private long pollIntervalMs = 2000;

    /** 是否在文档入库后异步预热 kkFileView 转换缓存 */
    private boolean warmupEnabled = true;

    /** 预热队列接口路径，kkFileView 默认 /addTask */
    private String warmupPath = "/addTask";

    /** 预热请求连接超时（毫秒） */
    private int warmupConnectTimeoutMs = 3000;

    /** 预热请求读取超时（毫秒） */
    private int warmupReadTimeoutMs = 5000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPreviewPath() {
        return previewPath;
    }

    public void setPreviewPath(String previewPath) {
        this.previewPath = previewPath;
    }

    public String getAsyncPreviewPath() {
        return asyncPreviewPath;
    }

    public void setAsyncPreviewPath(String asyncPreviewPath) {
        this.asyncPreviewPath = asyncPreviewPath;
    }

    public String getStatusPath() {
        return statusPath;
    }

    public void setStatusPath(String statusPath) {
        this.statusPath = statusPath;
    }

    public int getMaxPollAttempts() {
        return maxPollAttempts;
    }

    public void setMaxPollAttempts(int maxPollAttempts) {
        this.maxPollAttempts = maxPollAttempts;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public boolean isWarmupEnabled() {
        return warmupEnabled;
    }

    public void setWarmupEnabled(boolean warmupEnabled) {
        this.warmupEnabled = warmupEnabled;
    }

    public String getWarmupPath() {
        return warmupPath;
    }

    public void setWarmupPath(String warmupPath) {
        this.warmupPath = warmupPath;
    }

    public int getWarmupConnectTimeoutMs() {
        return warmupConnectTimeoutMs;
    }

    public void setWarmupConnectTimeoutMs(int warmupConnectTimeoutMs) {
        this.warmupConnectTimeoutMs = warmupConnectTimeoutMs;
    }

    public int getWarmupReadTimeoutMs() {
        return warmupReadTimeoutMs;
    }

    public void setWarmupReadTimeoutMs(int warmupReadTimeoutMs) {
        this.warmupReadTimeoutMs = warmupReadTimeoutMs;
    }
}
