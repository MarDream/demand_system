package com.demand.system.module.preview;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 文件预览服务。
 *
 * <p>将原始文件 URL 转换为外部预览服务的访问地址。
 * 业务代码只依赖 {@link PreviewService}，不直接耦合到具体第三方实现。</p>
 */
@Service
public class PreviewService {

    private static final String DEFAULT_WATERMARK_ANGLE = "45";

    private final PreviewProperties properties;

    public PreviewService(PreviewProperties properties) {
        this.properties = properties;
    }

    /**
     * 生成外部预览服务的访问 URL。
     *
     * @param fileUrl      原始文件 URL（http/https），将进行 base64 编码
     * @param watermarkTxt 水印文本（可空）
     * @return 完整的预览服务 URL
     */
    public String buildPreviewUrl(String fileUrl, String watermarkTxt) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new IllegalArgumentException("fileUrl 不能为空");
        }
        validateFileUrl(fileUrl);

        String base = normalizeBaseUrl(properties.getBaseUrl());
        String path = normalizePath(properties.getPreviewPath());

        StringBuilder query = new StringBuilder();
        query.append("url=").append(Base64.getEncoder().encodeToString(fileUrl.getBytes(StandardCharsets.UTF_8)));
        if (StringUtils.hasText(watermarkTxt)) {
            query.append("&watermarkTxt=").append(encode(watermarkTxt));
        }
        query.append("&watermarkAngle=").append(DEFAULT_WATERMARK_ANGLE);

        return base + path + "?" + query;
    }

    private void validateFileUrl(String fileUrl) {
        try {
            URI uri = new URI(fileUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("fileUrl 必须是 http 或 https 地址");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("fileUrl 格式不合法: " + e.getMessage());
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("文件预览服务 base-url 未配置");
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalStateException("文件预览服务 base-url 必须以 http:// 或 https:// 开头");
        }
        return trimmed;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/onlinePreview";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
