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

    /**
     * 浏览器侧可访问的预览地址前缀（<strong>同源相对路径</strong>，默认 {@code /kkfileview}）。
     *
     * <p>⚠️ 必须与 {@link #baseUrl} 分开 —— 这两个值描述的是<strong>两个不同的网络视角</strong>：
     * <ul>
     *   <li>{@code baseUrl}：<strong>后端容器内</strong>的视角，用于服务端调用 kkFileView 的
     *       {@code /officeSubmit}、{@code /getOfficeOnlineHtmlUrl} 等接口，必须是容器网络可达地址
     *       （如 {@code http://kkfileview:8012}）。</li>
     *   <li>{@code browserBaseUrl}：<strong>用户浏览器</strong>的视角，会作为 iframe 的 src 交给浏览器，
     *       必须与页面同源（相对路径即可）。</li>
     * </ul>
     *
     * <p>若两者共用一个 {@code baseUrl=http://kkfileview:8012}，会导致
     * ① HTTPS 页面里嵌 {@code http://} → 浏览器按 Mixed Content 直接拦截；
     * ② {@code kkfileview} 是 Docker 内部服务名，浏览器根本解析不了。</p>
     *
     * <p>取值只写路径前缀（如 {@code /kkfileview}）时由前端同源解析，因此
     * <strong>公网 HTTPS 入口与内网 HTTP 入口共用同一个值</strong>，不需要按环境切换。</p>
     */
    private String browserBaseUrl = "/kkfileview";

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

    public String getBrowserBaseUrl() {
        return browserBaseUrl;
    }

    public void setBrowserBaseUrl(String browserBaseUrl) {
        this.browserBaseUrl = browserBaseUrl;
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
