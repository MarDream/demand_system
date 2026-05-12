package com.demand.system.module.onlyoffice.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.onlyoffice.service.OnlyOfficeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/onlyoffice")
@RequiredArgsConstructor
public class OnlyOfficeController {

    private final OnlyOfficeService onlyOfficeService;

    @PostMapping("/editor-config")
    public Result<Map<String, Object>> getEditorConfig(
            @RequestParam Long knowledgeBaseId,
            @RequestParam Long documentId,
            @RequestParam(defaultValue = "edit") String mode) {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> config = onlyOfficeService.buildEditorConfig(knowledgeBaseId, documentId, userId, mode);
        return Result.success(config);
    }

    @PostMapping("/callback")
    public Result<Void> callback(@RequestBody Map<String, Object> callbackData) {
        onlyOfficeService.handleCallback(callbackData);
        return Result.success();
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        boolean available = onlyOfficeService.checkStatus();
        return Result.success(Map.of(
                "available", available,
                "message", available ? "OnlyOffice 服务可用" : "OnlyOffice 服务不可用"
        ));
    }
}
