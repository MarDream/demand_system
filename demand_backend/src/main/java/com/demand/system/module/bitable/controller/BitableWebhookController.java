package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableWebhookService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多维表格-Webhook 订阅管理控制器（需登录，ADMIN 及以上）
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableWebhookController {

    private final BitableWebhookService webhookService;
    private final BitableAuthorizationService authorizationService;

    public BitableWebhookController(BitableWebhookService webhookService,
                                    BitableAuthorizationService authorizationService) {
        this.webhookService = webhookService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/bases/{baseId}/webhooks")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> list(@PathVariable Long baseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(baseId, userId);
        return Result.success(webhookService.listByBase(baseId));
    }

    @PostMapping("/bases/{baseId}/webhooks")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> create(@PathVariable Long baseId,
                                              @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(baseId, userId);
        Long tableId = body.get("tableId") instanceof Number n ? n.longValue() : null;
        String name = body.get("name") != null ? String.valueOf(body.get("name")) : null;
        @SuppressWarnings("unchecked")
        List<String> eventTypes = (List<String>) body.get("eventTypes");
        String url = body.get("url") != null ? String.valueOf(body.get("url")) : null;
        return Result.success(webhookService.create(baseId, tableId, name, eventTypes, url, userId));
    }

    @PostMapping("/webhooks/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByWebhookId(id);
        authorizationService.checkManagePermission(baseId, userId);
        boolean enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        webhookService.updateStatus(id, enabled);
        return Result.success();
    }

    @DeleteMapping("/webhooks/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByWebhookId(id);
        authorizationService.checkManagePermission(baseId, userId);
        webhookService.delete(id);
        return Result.success();
    }
}
