package com.demand.system.module.onlyoffice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.result.Result;
import com.demand.system.module.file.storage.MinioStorageService;
import com.demand.system.module.knowledge.entity.KnowledgeDocument;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.demand.system.module.onlyoffice.config.OnlyOfficeConfig;
import com.demand.system.module.onlyoffice.service.OnlyOfficeService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
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
        KnowledgeDocument document = documentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getId, documentId)
                        .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
        );

        if (document == null) {
            throw new IllegalArgumentException("文档不存在");
        }

        String fileType = document.getFileType();
        if (!SUPPORTED_TYPES.contains(fileType.toLowerCase())) {
            throw new IllegalArgumentException("OnlyOffice 不支持该文件类型: " + fileType);
        }

        User user = userMapper.selectById(userId);
        String userName = user != null ? (user.getRealName() != null ? user.getRealName() : user.getUsername()) : "Guest";

        try {
            String presignedUrl = buildOnlyOfficeDocumentUrl(document.getMinioKey());
            String callbackUrl = onlyOfficeConfig.getCallbackUrl();

            String documentKey = documentId + "_" + (document.getUpdatedAt() != null ?
                    document.getUpdatedAt().toString().replace("-", "").replace(":", "").replace(" ", "T") :
                    LocalDateTime.now().toString());

            Map<String, Object> documentConfig = new LinkedHashMap<>();
            documentConfig.put("key", documentKey);
            documentConfig.put("url", presignedUrl);
            documentConfig.put("title", document.getFileName());
            documentConfig.put("fileType", fileType.toLowerCase());

            Map<String, Object> editorConfig = new LinkedHashMap<>();
            editorConfig.put("callbackUrl", callbackUrl + "?docId=" + documentId + "&kbId=" + knowledgeBaseId);

            Map<String, Object> userConfig = new LinkedHashMap<>();
            userConfig.put("id", userId != null ? userId.toString() : "0");
            userConfig.put("name", userName);
            editorConfig.put("user", userConfig);

            editorConfig.put("mode", "view".equals(mode) ? "view" : "edit");

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("document", documentConfig);
            config.put("editorConfig", editorConfig);

            String token = generateToken(config);
            config.put("token", token);

            config.put("type", "desktop".equals(mode) ? "desktop" : "embedded");
            config.put("height", "100%");
            config.put("width", "100%");

            return config;

        } catch (Exception e) {
            log.error("构建 OnlyOffice 配置失败", e);
            throw new RuntimeException("构建编辑器配置失败: " + e.getMessage());
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

    private String buildOnlyOfficeDocumentUrl(String minioKey) throws Exception {
        String targetEndpoint = onlyOfficeConfig.getDocumentAccessEndpoint();
        if (targetEndpoint == null || targetEndpoint.isBlank()) {
            return minioStorageService.getPresignedUrl(minioKey, 1);
        }
        return minioStorageService.getPresignedUrl(minioKey, 1, targetEndpoint);
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
}
