package com.demand.system.module.onlyoffice.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.onlyoffice.config.OnlyOfficeConfig;
import com.demand.system.module.onlyoffice.service.OnlyOfficeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/onlyoffice")
@RequiredArgsConstructor
public class OnlyOfficeController {

    private final OnlyOfficeService onlyOfficeService;
    private final OnlyOfficeConfig onlyOfficeConfig;

    @PostMapping("/editor-config")
    public Result<Map<String, Object>> getEditorConfig(
            @RequestParam Long knowledgeBaseId,
            @RequestParam Long documentId,
            @RequestParam(defaultValue = "edit") String mode) {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> config = onlyOfficeService.buildEditorConfig(knowledgeBaseId, documentId, userId, mode);
        return Result.success(config);
    }

    @GetMapping("/files/{documentId}")
    public void downloadDocument(
            @RequestParam Long knowledgeBaseId,
            @PathVariable Long documentId,
            @RequestParam String accessToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        onlyOfficeService.downloadDocument(knowledgeBaseId, documentId, accessToken, request, response);
    }

    @PostMapping("/public/editor-config")
    public Result<Map<String, Object>> getPublicEditorConfig(
            @RequestParam String accessToken,
            @RequestParam(defaultValue = "view") String mode) {
        Map<String, Object> config = onlyOfficeService.buildPublicEditorConfig(accessToken, mode);
        return Result.success(config);
    }

    @GetMapping("/public/files/{documentId}")
    public void downloadSharedDocument(
            @PathVariable Long documentId,
            @RequestParam String accessToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        onlyOfficeService.downloadSharedDocument(documentId, accessToken, request, response);
    }

    @PostMapping("/callback")
    public Result<Void> callback(@RequestBody Map<String, Object> callbackData) {
        onlyOfficeService.handleCallback(callbackData);
        return Result.success();
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        boolean available = onlyOfficeService.checkStatus();
        String serverUrl = onlyOfficeConfig.getServerUrl();
        if (serverUrl != null && serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
        }
        return Result.success(Map.of(
                "available", available,
                "message", available ? "文档编辑服务可用" : "文档编辑服务不可用",
                "apiJsUrl", serverUrl + "/web-apps/apps/api/documents/api.js"
        ));
    }
}
