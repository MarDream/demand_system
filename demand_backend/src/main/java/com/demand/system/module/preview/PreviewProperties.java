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

    /** 预览接口路径，默认 /onlinePreview */
    private String previewPath = "/onlinePreview";

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
}
