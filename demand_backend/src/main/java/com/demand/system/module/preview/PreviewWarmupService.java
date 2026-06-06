package com.demand.system.module.preview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * kkFileView 预热服务。
 *
 * <p>把文档提前放入 kkFileView 转换队列，减少用户首次打开大文件时的等待时间。
 * 预热是尽力而为：kkFileView 不可用或转换失败不会影响上传、入库等主流程。</p>
 */
@Service
public class PreviewWarmupService {

    private static final Logger log = LoggerFactory.getLogger(PreviewWarmupService.class);

    private final PreviewProperties properties;
    private final RestTemplate restTemplate;

    public PreviewWarmupService(PreviewProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getWarmupConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getWarmupReadTimeoutMs());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Async
    public void warmup(String fileUrl, String fileName) {
        if (!properties.isWarmupEnabled() || !StringUtils.hasText(fileUrl)) {
            return;
        }
        try {
            String url = buildWarmupUrl(fileUrl);
            String response = restTemplate.getForObject(url, String.class);
            if (!"success".equalsIgnoreCase(response == null ? "" : response.trim())) {
                log.warn("kkFileView 预热任务提交未返回 success: fileName={}, response={}", fileName, response);
                return;
            }
            log.info("kkFileView 预热任务已提交: fileName={}", fileName);
        } catch (Exception e) {
            log.warn("kkFileView 预热任务提交失败: fileName={}", fileName, e);
        }
    }

    private String buildWarmupUrl(String fileUrl) {
        String base = normalizeBaseUrl(properties.getBaseUrl());
        String path = normalizePath(properties.getWarmupPath());
        String encodedUrl = Base64.getEncoder().encodeToString(fileUrl.getBytes(StandardCharsets.UTF_8));
        return base + path + "?url=" + URLEncoder.encode(encodedUrl, StandardCharsets.UTF_8);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("文件预览服务 base-url 未配置");
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/addTask";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
