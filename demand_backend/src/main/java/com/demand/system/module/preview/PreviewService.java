package com.demand.system.module.preview;

import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import com.demand.system.module.preview.dto.AsyncPreviewVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 文件预览服务。
 *
 * <p>将原始文件 URL 转换为外部预览服务的访问地址。
 * 业务代码只依赖 {@link PreviewService}，不直接耦合到具体第三方实现。</p>
 *
 * <p>对于知识库文档，预签名 MinIO URL 的签发与过期控制由本服务统一负责：
 * 每次打开预览都会重新签发一个长有效期 URL，避免 kkFileView 异步转码 + 浏览器渲染过程中 URL 过期。</p>
 */
@Service
public class PreviewService {

    private static final String DEFAULT_WATERMARK_ANGLE = "30";
    private static final String DEFAULT_WATERMARK_X_SPACE = "600";
    private static final String DEFAULT_WATERMARK_Y_SPACE = "80";
    private static final String DEFAULT_WATERMARK_FONT_SIZE = "14";
    private static final String DEFAULT_WATERMARK_COLOR = "cccccc";
    private static final String DEFAULT_WATERMARK_COLS = "2";
    private static final String DEFAULT_WATERMARK_ROWS = "1";

    /**
     * 预览场景下 MinIO 预签名 URL 有效期（小时）。
     *
     * <p>原方案由前端先调 {@code /preview} 拿到 1 小时 URL 再喂给 kkFileView，
     * 但 kkFileView 异步转码 + 浏览器渲染链路总耗时可能超过 1 小时，导致
     * {@code /getCorsFile} 拉取文件时 403 expired。此处改为 24 小时兜底正常使用窗口，
     * 用户每次重新打开预览时都会签发一个全新 URL。</p>
     */
    private static final int PREVIEW_PRESIGN_HOURS = 24;

    private final PreviewProperties properties;
    private final RestTemplate restTemplate;
    private final KnowledgeDocumentService knowledgeDocumentService;

    public PreviewService(PreviewProperties properties, KnowledgeDocumentService knowledgeDocumentService) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    /**
     * 生成外部预览服务的访问 URL（同步模式）。
     *
     * @param fileUrl      原始文件 URL（http/https），将进行 base64 编码
     * @param watermarkTxt 水印文本（可空）
     * @return 完整的预览服务 URL
     */
    public String buildPreviewUrl(String fileUrl, String watermarkTxt) {
        return buildPreviewUrlInternal(fileUrl, watermarkTxt);
    }

    /**
     * 知识库文档同步预览：先签发一个长有效期的 MinIO URL，再交给预览服务。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param watermarkTxt    水印文本（可空）
     * @return 完整的预览服务 URL
     */
    public String buildPreviewUrl(Long knowledgeBaseId, Long documentId, String watermarkTxt) {
        String presignedUrl = resolvePresignedUrl(knowledgeBaseId, documentId);
        return buildPreviewUrlInternal(presignedUrl, watermarkTxt);
    }

    /**
     * 异步预览（推荐入口）：提交转换任务，立即返回 taskId。
     *
     * <p>该方法只负责"提交"环节，<strong>不阻塞等待</strong>。调用方拿到 taskId 后
     * 通过 {@link #pollAsyncPreviewStatus(String)} 轮询状态。
     * 这种拆分解决了原 {@code office-async} 同步阻塞模式下大文件转码超过前端
     * axios 15s 默认超时导致 {@code timeout of 15000ms exceeded} 的问题。</p>
     *
     * <p>响应场景：
     * <ul>
     *   <li>小文件已转完 → {@link AsyncPreviewVO#completed(String)}</li>
     *   <li>大文件排队/转换中 → {@link AsyncPreviewVO#processing(String)}，需后续轮询</li>
     *   <li>异常时降级为同步模式 → {@link AsyncPreviewVO#completed(String)}</li>
     * </ul>
     */
    public AsyncPreviewVO submitAsyncPreview(String fileUrl, String watermarkTxt) {
        return submitAsyncPreviewInternal(fileUrl, watermarkTxt);
    }

    /**
     * 知识库文档异步预览提交：先签发一个长有效期 MinIO URL，再走异步转码流程。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param watermarkTxt    水印文本（可空）
     * @return 异步预览结果（processing 表示需轮询，completed 表示已转完）
     */
    public AsyncPreviewVO submitAsyncPreview(Long knowledgeBaseId, Long documentId, String watermarkTxt) {
        String presignedUrl = resolvePresignedUrl(knowledgeBaseId, documentId);
        return submitAsyncPreviewInternal(presignedUrl, watermarkTxt);
    }

    /**
     * 轮询异步预览任务状态。
     *
     * <p>由前端在收到 {@code status=processing} 的响应后周期调用（推荐 2 秒一次）。
     * 后端内部仅做单次 HTTP 转发，不在前端 axios 15s 超时窗口内阻塞。</p>
     *
     * <p>{@code previewUrl} 由前端从 submit 响应中缓存后传入（kkFileView 的
     * {@code /getOfficeOnlineHtmlUrl} 在 status=2 时只返回相对路径 {@code /onlinePreview?cacheName=xxx}，
     * 不能直接作为 iframe src 使用）。</p>
     *
     * @param taskId     kkFileView 异步任务 ID
     * @param previewUrl 前端缓存的完整预览 URL（submit 阶段拿到），用于 completed 时回填
     * @return 当前状态
     */
    public AsyncPreviewVO pollAsyncPreviewStatus(String taskId, String previewUrl) {
        if (!StringUtils.hasText(taskId)) {
            return AsyncPreviewVO.failed("taskId 不能为空");
        }
        String statusUrl = buildStatusUrl(taskId);
        try {
            String response = restTemplate.getForObject(statusUrl, String.class);
            AsyncPreviewVO result = parseStatusResponse(response);
            // 状态查询不带可用的 previewUrl，强制使用前端缓存的 URL（kkFileView 返回的是相对路径）
            if (StringUtils.hasText(previewUrl)) {
                result.setPreviewUrl(previewUrl);
            }
            return result;
        } catch (Exception e) {
            return AsyncPreviewVO.failed("查询预览状态失败: " + e.getMessage());
        }
    }

    /**
     * 同步模式异步预览（兼容保留）：阻塞等待转换完成。
     *
     * <p>供内部或后端批处理使用；前端请走 {@link #submitAsyncPreview} +
     * {@link #pollAsyncPreviewStatus}。</p>
     */
    public AsyncPreviewVO buildAsyncPreviewUrl(String fileUrl, String watermarkTxt) {
        AsyncPreviewVO submitted = submitAsyncPreviewInternal(fileUrl, watermarkTxt);
        return awaitCompletion(submitted);
    }

    /**
     * 知识库文档同步模式异步预览（兼容保留）。
     */
    public AsyncPreviewVO buildAsyncPreviewUrl(Long knowledgeBaseId, Long documentId, String watermarkTxt) {
        String presignedUrl = resolvePresignedUrl(knowledgeBaseId, documentId);
        AsyncPreviewVO submitted = submitAsyncPreviewInternal(presignedUrl, watermarkTxt);
        return awaitCompletion(submitted);
    }

    /**
     * 从 kkFileView 提交响应中提取 taskId。
     *
     * <p>kkFileView 返回示例: {"taskId":"abc123","status":0}
     * 如果返回 HTML 内容则说明直接转换完成，无 taskId</p>
     */
    @SuppressWarnings("unchecked")
    private String extractTaskId(String response) {
        try {
            if (response == null || response.trim().startsWith("<")) {
                return null; // 直接返回了 HTML，说明转换完成
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> map = mapper.readValue(response, Map.class);
            Object taskId = map.get("taskId");
            return taskId != null ? taskId.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 kkFileView 状态查询响应。
     *
     * <p>kkFileView 状态响应示例:
     * <ul>
     *   <li>{"status":0,"taskId":"xxx"} - 排队中</li>
     *   <li>{"status":1,"taskId":"xxx"} - 转换中</li>
     *   <li>{"status":2,"taskId":"xxx","convertUrl":"http://..."} - 完成</li>
     *   <li>{"status":-1,"taskId":"xxx","message":"error"} - 失败</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private AsyncPreviewVO parseStatusResponse(String response) {
        try {
            if (response == null || response.trim().startsWith("<")) {
                return AsyncPreviewVO.processing(null);
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> map = mapper.readValue(response, Map.class);

            Integer status = map.get("status") != null ? ((Number) map.get("status")).intValue() : -1;
            String taskId = map.get("taskId") != null ? map.get("taskId").toString() : null;
            String convertUrl = map.get("convertUrl") != null ? map.get("convertUrl").toString() : null;
            String message = map.get("message") != null ? map.get("message").toString() : null;

            return switch (status) {
                case 0, 1 -> AsyncPreviewVO.processing(taskId);
                case 2 -> AsyncPreviewVO.completed(convertUrl);
                default -> AsyncPreviewVO.failed(message != null ? message : "预览转换失败");
            };
        } catch (Exception e) {
            return AsyncPreviewVO.failed("解析预览状态失败: " + e.getMessage());
        }
    }

    /**
     * 知识库文档 → 长有效期 MinIO 预签名 URL。
     */
    private String resolvePresignedUrl(Long knowledgeBaseId, Long documentId) {
        return knowledgeDocumentService.getPreviewUrl(knowledgeBaseId, documentId, PREVIEW_PRESIGN_HOURS);
    }

    private String buildPreviewUrlInternal(String fileUrl, String watermarkTxt) {
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
        query.append("&watermarkXSpace=").append(DEFAULT_WATERMARK_X_SPACE);
        query.append("&watermarkYSpace=").append(DEFAULT_WATERMARK_Y_SPACE);
        query.append("&watermarkFontSize=").append(DEFAULT_WATERMARK_FONT_SIZE);
        query.append("&watermarkColor=").append(DEFAULT_WATERMARK_COLOR);
        query.append("&watermarkCols=").append(DEFAULT_WATERMARK_COLS);
        query.append("&watermarkRows=").append(DEFAULT_WATERMARK_ROWS);

        return base + path + "?" + query;
    }

    private AsyncPreviewVO submitAsyncPreviewInternal(String fileUrl, String watermarkTxt) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new IllegalArgumentException("fileUrl 不能为空");
        }
        validateFileUrl(fileUrl);

        // 预先拼好 previewUrl，让前端缓存；kkFileView 转码完成后打开该 URL 即可秒级返回
        String previewUrl = buildPreviewUrlInternal(fileUrl, watermarkTxt);

        String submitFullUrl = buildSubmitUrl(fileUrl, watermarkTxt);
        try {
            String submitResponse = restTemplate.getForObject(submitFullUrl, String.class);
            String taskId = extractTaskId(submitResponse);

            if (taskId == null) {
                // submit 响应无 taskId：降级为同步模式（兼容老 kkFileView 或异常）
                return AsyncPreviewVO.completed(previewUrl);
            }
            // 提交成功：返回 processing + 缓存的 previewUrl + taskId
            return AsyncPreviewVO.processing(taskId, previewUrl);
        } catch (Exception e) {
            // 提交异常时降级为同步模式
            return AsyncPreviewVO.completed(previewUrl);
        }
    }

    /**
     * 同步模式：阻塞轮询直到任务完成。仅供后端批处理或兼容入口使用。
     *
     * <p>注意：这里使用 submit 阶段缓存的 previewUrl，轮询只关心 status 变化。
     * 转码完成后由前端把缓存的 previewUrl 嵌入 iframe。</p>
     */
    private AsyncPreviewVO awaitCompletion(AsyncPreviewVO submitted) {
        if (!"processing".equals(submitted.getStatus()) || submitted.getTaskId() == null) {
            return submitted;
        }
        String statusUrl = buildStatusUrl(submitted.getTaskId());
        try {
            for (int i = 0; i < properties.getMaxPollAttempts(); i++) {
                TimeUnit.MILLISECONDS.sleep(properties.getPollIntervalMs());
                String response = restTemplate.getForObject(statusUrl, String.class);
                AsyncPreviewVO result = parseStatusResponse(response);
                // 状态查询不返回 previewUrl，复用 submit 阶段的 URL
                result.setPreviewUrl(submitted.getPreviewUrl());
                if ("completed".equals(result.getStatus()) || "failed".equals(result.getStatus())) {
                    return result;
                }
            }
            return AsyncPreviewVO.failed("预览转换超时，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AsyncPreviewVO.failed("预览转换被中断");
        }
    }

    private String buildSubmitUrl(String fileUrl, String watermarkTxt) {
        String base = normalizeBaseUrl(properties.getBaseUrl());
        String submitPath = normalizePath(properties.getAsyncPreviewPath());
        String encodedFileUrl = Base64.getEncoder().encodeToString(fileUrl.getBytes(StandardCharsets.UTF_8));
        StringBuilder submitQuery = new StringBuilder();
        submitQuery.append("url=").append(encodedFileUrl);
        if (StringUtils.hasText(watermarkTxt)) {
            submitQuery.append("&watermarkTxt=").append(encode(watermarkTxt));
        }
        submitQuery.append("&watermarkAngle=").append(DEFAULT_WATERMARK_ANGLE);
        submitQuery.append("&watermarkXSpace=").append(DEFAULT_WATERMARK_X_SPACE);
        submitQuery.append("&watermarkYSpace=").append(DEFAULT_WATERMARK_Y_SPACE);
        submitQuery.append("&watermarkFontSize=").append(DEFAULT_WATERMARK_FONT_SIZE);
        submitQuery.append("&watermarkColor=").append(DEFAULT_WATERMARK_COLOR);
        submitQuery.append("&watermarkCols=").append(DEFAULT_WATERMARK_COLS);
        submitQuery.append("&watermarkRows=").append(DEFAULT_WATERMARK_ROWS);
        return base + submitPath + "?" + submitQuery;
    }

    private String buildStatusUrl(String taskId) {
        String base = normalizeBaseUrl(properties.getBaseUrl());
        String statusPath = normalizePath(properties.getStatusPath());
        return base + statusPath + "?taskId=" + taskId;
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
