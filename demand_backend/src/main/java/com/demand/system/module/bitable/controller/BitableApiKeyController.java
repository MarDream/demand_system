package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.entity.BitableApiCredential;
import com.demand.system.module.bitable.entity.BitableTable;
import com.demand.system.module.bitable.mapper.BitableTableMapper;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableFieldService;
import com.demand.system.module.bitable.service.BitableOpenApiService;
import com.demand.system.module.bitable.service.BitableRecordService;
import com.demand.system.module.bitable.dto.RecordQueryDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 多维表格-开放 API 管理控制器（需登录，管理凭证）
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableApiKeyController {

    private final BitableOpenApiService openApiService;
    private final BitableAuthorizationService authorizationService;

    public BitableApiKeyController(BitableOpenApiService openApiService,
                                   BitableAuthorizationService authorizationService) {
        this.openApiService = openApiService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/bases/{baseId}/api-keys")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> createKey(@PathVariable Long baseId,
                                                 @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkOwnerPermission(baseId, userId);
        String name = body.get("name") != null ? String.valueOf(body.get("name")) : null;
        @SuppressWarnings("unchecked")
        List<String> scopes = (List<String>) body.get("scopes");
        LocalDateTime expireAt = parseDateTime(body.get("expireAt"));
        return Result.success(openApiService.createCredential(baseId, name, scopes, expireAt, userId));
    }

    @GetMapping("/bases/{baseId}/api-keys")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> listKeys(@PathVariable Long baseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(baseId, userId);
        return Result.success(openApiService.listByBase(baseId));
    }

    @DeleteMapping("/api-keys/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> revokeKey(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkOwnerPermission(openApiService.getBaseIdByCredentialId(id), userId);
        openApiService.revoke(id);
        return Result.success();
    }

    private LocalDateTime parseDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.valueOf(raw));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
