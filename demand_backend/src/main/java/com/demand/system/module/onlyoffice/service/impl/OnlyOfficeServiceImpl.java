package com.demand.system.module.onlyoffice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.file.storage.MinioStorageService;
import com.demand.system.module.knowledge.entity.KnowledgeDocument;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.demand.system.module.onlyoffice.config.OnlyOfficeConfig;
import com.demand.system.module.onlyoffice.service.OnlyOfficeService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlyOfficeServiceImpl implements OnlyOfficeService {

    private final OnlyOfficeConfig onlyOfficeConfig;
    private final KnowledgeDocumentMapper documentMapper;
    private final UserMapper userMapper;
    private final MinioStorageService minioStorageService;

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    @Override
    public Map<String, Object> buildEditorConfig(Long knowledgeBaseId, Long documentId, Long userId, String mode) {
        KnowledgeDocument document = getDocument(knowledgeBaseId, documentId);

        String fileType = Optional.ofNullable(document.getFileType()).orElse("").toLowerCase();
        if (!SUPPORTED_TYPES.contains(fileType.toLowerCase())) {
            throw new IllegalArgumentException("OnlyOffice 不支持该文件类型: " + fileType);
        }

        User user = userMapper.selectById(userId);
        String userName = user != null ? (user.getRealName() != null ? user.getRealName() : user.getUsername()) : "Guest";

        try {
            String documentUrl = buildOnlyOfficeDocumentUrl(knowledgeBaseId, documentId, document.getMinioKey());
            String callbackUrl = onlyOfficeConfig.getCallbackUrl();
            boolean editMode = !"view".equals(mode);

            String documentKey = documentId + "_" + (document.getUpdatedAt() != null ?
                    document.getUpdatedAt().toString().replace("-", "").replace(":", "").replace(" ", "T") :
                    LocalDateTime.now().toString());

            Map<String, Object> documentConfig = new LinkedHashMap<>();
            documentConfig.put("key", documentKey);
            documentConfig.put("url", documentUrl);
            documentConfig.put("title", document.getFileName());
            documentConfig.put("fileType", fileType);
            documentConfig.put("permissions", Map.of(
                    "edit", editMode,
                    "download", true,
                    "print", true
            ));

            Map<String, Object> editorConfig = new LinkedHashMap<>();
            editorConfig.put("callbackUrl", callbackUrl + "?docId=" + documentId + "&kbId=" + knowledgeBaseId);

            Map<String, Object> userConfig = new LinkedHashMap<>();
            userConfig.put("id", userId != null ? userId.toString() : "0");
            userConfig.put("name", userName);
            editorConfig.put("user", userConfig);

            editorConfig.put("mode", editMode ? "edit" : "view");

            Map<String, Object> customization = new LinkedHashMap<>();
            customization.put("about", false);
            customization.put("feedback", false);
            customization.put("compactHeader", true);
            customization.put("compactToolbar", true);
            Map<String, Object> logoConfig = new LinkedHashMap<>();
            logoConfig.put("image", "");
            logoConfig.put("imageEmbedded", "");
            logoConfig.put("imageDark", "");
            logoConfig.put("imageDarkEmbedded", "");
            customization.put("logo", logoConfig);
            Map<String, Object> features = new LinkedHashMap<>();
            features.put("spellcheck", editMode);
            customization.put("features", features);
            editorConfig.put("customization", customization);

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("document", documentConfig);
            config.put("documentType", resolveDocumentType(fileType));
            config.put("editorConfig", editorConfig);

            String token = generateToken(config);
            config.put("token", token);

            config.put("type", "desktop");
            config.put("height", "100%");
            config.put("width", "100%");

            return config;

        } catch (Exception e) {
            log.error("构建 OnlyOffice 配置失败", e);
            throw new RuntimeException("构建编辑器配置失败: " + e.getMessage());
        }
    }

    @Override
    public void downloadDocument(Long knowledgeBaseId, Long documentId, String accessToken,
                                 HttpServletRequest request, HttpServletResponse response) {
        if (!isDocumentAccessTokenValid(knowledgeBaseId, documentId, accessToken)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "OnlyOffice 文件访问令牌无效");
            return;
        }

        KnowledgeDocument document = getDocument(knowledgeBaseId, documentId);
        String rangeHeader = request.getHeader("Range");
        boolean headOnly = "HEAD".equalsIgnoreCase(request.getMethod());

        try {
            long fileSize = resolveFileSize(document);
            String encodedName = URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");

            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Disposition", "inline; filename*=UTF-8''" + encodedName);
            response.setContentType(getContentType(document.getFileType()));

            ByteRange range = parseRange(rangeHeader, fileSize);
            if (rangeHeader != null && range == null) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileSize);
                return;
            }

            if (range != null) {
                long length = range.end() - range.start() + 1;
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", "bytes " + range.start() + "-" + range.end() + "/" + fileSize);
                response.setContentLengthLong(length);
                if (headOnly) {
                    return;
                }
                try (InputStream is = minioStorageService.download(document.getMinioKey(), range.start(), length)) {
                    is.transferTo(response.getOutputStream());
                }
            } else {
                response.setContentLengthLong(fileSize);
                if (headOnly) {
                    return;
                }
                try (InputStream is = minioStorageService.download(document.getMinioKey())) {
                    is.transferTo(response.getOutputStream());
                }
            }

            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("OnlyOffice 文件访问失败: kbId={}, docId={}", knowledgeBaseId, documentId, e);
            if (!response.isCommitted()) {
                sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "OnlyOffice 文件访问失败");
            }
        }
    }

    @Override
    public void handleCallback(Map<String, Object> callbackData) {
        Integer status = (Integer) callbackData.get("status");
        String key = (String) callbackData.get("key");
        String url = (String) callbackData.get("url");

        log.info("OnlyOffice 回调: status={}, key={}", status, key);

        if (status == null) {
            return;
        }

        if (status == 2 || status == 6) {
            if (key == null || url == null) {
                log.warn("OnlyOffice 回调缺少必要参数");
                return;
            }

            try {
                Long docId = extractDocIdFromKey(key);
                if (docId == null) {
                    log.warn("无法从 key 解析文档 ID: {}", key);
                    return;
                }

                KnowledgeDocument document = documentMapper.selectById(docId);
                if (document == null) {
                    log.warn("文档不存在: {}", docId);
                    return;
                }

                downloadAndSaveDocument(url, document);

                document.setUpdatedAt(LocalDateTime.now());
                documentMapper.updateById(document);

                log.info("文档保存成功: {}", docId);
            } catch (Exception e) {
                log.error("处理 OnlyOffice 回调失败", e);
            }
        }
    }

    @Override
    public boolean checkStatus() {
        try {
            String serverUrl = onlyOfficeConfig.getServerUrl();
            String checkUrl = serverUrl.replace("https", "http") + "/welcome/";
            RestTemplate restTemplate = new RestTemplate();
            var response = restTemplate.getForEntity(checkUrl, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("OnlyOffice 服务不可用: {}", e.getMessage());
            return false;
        }
    }

    private String generateToken(Map<String, Object> config) {
        String secret = onlyOfficeConfig.getJwtSecret();
        if (secret == null || secret.isEmpty()) {
            return "";
        }

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("onlyoffice")
                .claim("document", config.get("document"))
                .claim("editorConfig", config.get("editorConfig"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    private String buildOnlyOfficeDocumentUrl(Long knowledgeBaseId, Long documentId, String minioKey) {
        String baseUrl = resolveDocumentAccessBaseUrl();
        String accessToken = generateDocumentAccessToken(knowledgeBaseId, documentId, minioKey);

        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/v1/onlyoffice/files/{documentId}")
                .queryParam("knowledgeBaseId", knowledgeBaseId)
                .queryParam("accessToken", accessToken)
                .buildAndExpand(documentId)
                .toUriString();
    }

    private String resolveDocumentAccessBaseUrl() {
        String endpoint = onlyOfficeConfig.getDocumentAccessEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        }

        String callbackUrl = onlyOfficeConfig.getCallbackUrl();
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new IllegalStateException("未配置 OnlyOffice 文档访问地址");
        }
        int pathIndex = callbackUrl.indexOf("/api/");
        return pathIndex > 0 ? callbackUrl.substring(0, pathIndex) : callbackUrl;
    }

    private String generateDocumentAccessToken(Long knowledgeBaseId, Long documentId, String minioKey) {
        SecretKey key = Keys.hmacShaKeyFor(onlyOfficeConfig.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("onlyoffice-file")
                .claim("knowledgeBaseId", knowledgeBaseId)
                .claim("documentId", documentId)
                .claim("minioKey", minioKey)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    private boolean isDocumentAccessTokenValid(Long knowledgeBaseId, Long documentId, String accessToken) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(onlyOfficeConfig.getJwtSecret().getBytes(StandardCharsets.UTF_8));
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
            Object tokenKbId = claims.get("knowledgeBaseId");
            Object tokenDocId = claims.get("documentId");
            return Objects.equals(knowledgeBaseId, toLong(tokenKbId))
                    && Objects.equals(documentId, toLong(tokenDocId));
        } catch (Exception e) {
            log.warn("OnlyOffice 文件访问令牌校验失败: {}", e.getMessage());
            return false;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            return Long.parseLong(str);
        }
        return null;
    }

    private KnowledgeDocument getDocument(Long knowledgeBaseId, Long documentId) {
        KnowledgeDocument document = documentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getId, documentId)
                        .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
        );
        if (document == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        return document;
    }

    private String resolveDocumentType(String fileType) {
        return switch (fileType.toLowerCase()) {
            case "doc", "docx" -> "word";
            case "xls", "xlsx" -> "cell";
            case "ppt", "pptx" -> "slide";
            default -> throw new IllegalArgumentException("OnlyOffice 不支持该文件类型: " + fileType);
        };
    }

    private long resolveFileSize(KnowledgeDocument document) throws Exception {
        if (document.getFileSize() != null && document.getFileSize() > 0) {
            return document.getFileSize();
        }
        return minioStorageService.stat(document.getMinioKey()).size();
    }

    private ByteRange parseRange(String rangeHeader, long fileSize) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return null;
        }
        if (!rangeHeader.startsWith("bytes=") || rangeHeader.contains(",")) {
            return null;
        }

        String value = rangeHeader.substring("bytes=".length()).trim();
        String[] parts = value.split("-", 2);
        if (parts.length != 2) {
            return null;
        }

        try {
            long start;
            long end;

            if (parts[0].isBlank()) {
                long suffixLength = Long.parseLong(parts[1]);
                if (suffixLength <= 0) {
                    return null;
                }
                start = Math.max(0, fileSize - suffixLength);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(parts[0]);
                end = parts[1].isBlank() ? fileSize - 1 : Long.parseLong(parts[1]);
            }

            if (start < 0 || start >= fileSize || end < start) {
                return null;
            }

            return new ByteRange(start, Math.min(end, fileSize - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void sendError(HttpServletResponse response, int statusCode, String message) {
        try {
            response.sendError(statusCode, message);
        } catch (Exception e) {
            log.warn("写入 OnlyOffice 错误响应失败: {}", e.getMessage());
        }
    }

    private Long extractDocIdFromKey(String key) {
        if (key == null || !key.contains("_")) {
            return null;
        }
        try {
            return Long.parseLong(key.split("_")[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void downloadAndSaveDocument(String url, KnowledgeDocument document) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            var response = restTemplate.getForEntity(url, byte[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String contentType = getContentType(document.getFileType());
                minioStorageService.delete(document.getMinioKey());
                minioStorageService.upload(
                        new java.io.ByteArrayInputStream(response.getBody()),
                        document.getMinioKey(),
                        contentType
                );
            }
        } catch (Exception e) {
            log.error("下载并保存文档失败: {}", e.getMessage());
            throw new RuntimeException("文档保存失败: " + e.getMessage());
        }
    }

    private String getContentType(String fileType) {
        return switch (fileType.toLowerCase()) {
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };
    }

    private record ByteRange(long start, long end) {
    }
}
