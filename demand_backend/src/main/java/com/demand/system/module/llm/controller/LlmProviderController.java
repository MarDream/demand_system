package com.demand.system.module.llm.controller;

import com.demand.system.module.llm.constant.LlmModelRole;
import com.demand.system.module.llm.dto.*;
import com.demand.system.module.llm.service.LlmProviderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/llm-providers")
public class LlmProviderController {
    private final LlmProviderService providerService;

    public LlmProviderController(LlmProviderService providerService) {
        this.providerService = providerService;
    }

    // ==================== Provider ====================

    @GetMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功", "data", providerService.list()));
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功", "data", providerService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:create')")
    public ResponseEntity<Map<String, Object>> create(@Validated @RequestBody LlmProviderDTO dto) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "创建成功", "data", providerService.create(dto)));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Validated @RequestBody LlmProviderDTO dto) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功", "data", providerService.update(id, dto)));
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:delete')")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        providerService.delete(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    @PatchMapping("/{id:\\d+}/toggle")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public ResponseEntity<Map<String, Object>> toggleEnabled(@PathVariable Long id) {
        providerService.toggleEnabled(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功"));
    }

    @GetMapping("/{id:\\d+}/api-key")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getApiKey(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功", "data", Map.of("apiKey", providerService.getApiKey(id))));
    }

    // ==================== Model ====================

    @PostMapping("/{id:\\d+}/models")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:create')")
    public ResponseEntity<Map<String, Object>> addModel(
            @PathVariable Long id,
            @Validated @RequestBody LlmModelDTO dto) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "创建成功", "data", providerService.addModel(id, dto)));
    }

    @PutMapping("/{id:\\d+}/models/{modelId:\\d+}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public ResponseEntity<Map<String, Object>> updateModel(
            @PathVariable Long id,
            @PathVariable Long modelId,
            @Validated @RequestBody LlmModelDTO dto) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功", "data", providerService.updateModel(modelId, dto)));
    }

    @DeleteMapping("/{id:\\d+}/models/{modelId:\\d+}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:delete')")
    public ResponseEntity<Map<String, Object>> deleteModel(
            @PathVariable Long id,
            @PathVariable Long modelId) {
        providerService.deleteModel(modelId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    @PatchMapping("/{id:\\d+}/models/{modelId:\\d+}/toggle")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public ResponseEntity<Map<String, Object>> toggleModelEnabled(
            @PathVariable Long id,
            @PathVariable Long modelId) {
        providerService.toggleModelEnabled(modelId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功"));
    }

    @PatchMapping("/{id:\\d+}/models/{modelId:\\d+}/toggle-default")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public ResponseEntity<Map<String, Object>> toggleModelDefault(
            @PathVariable Long id,
            @PathVariable Long modelId) {
        providerService.toggleModelDefault(modelId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功"));
    }

    @PostMapping("/{id:\\d+}/models/{modelId:\\d+}/test")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:test')")
    public ResponseEntity<Map<String, Object>> testModel(
            @PathVariable Long id,
            @PathVariable Long modelId,
            @Validated @RequestBody LlmTestRequestDTO request) {
        LlmTestResultVO result = providerService.testModel(modelId, request);
        return ResponseEntity.ok(Map.of("code", 200, "message", "测试完成", "data", result));
    }

    // ==================== Roles ====================

    @GetMapping("/models/roles")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getRoles() {
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功", "data", LlmModelRole.PRESET_ROLES));
    }

    // ==================== Chat Models (for RAG) ====================

    /**
     * 获取可用的 Chat 模型列表（供 RAG 文档中心使用）。
     * 仅返回已启用的 provider 下已启用且非 embedding/rerank 类型的模型，
     * 不暴露 API Key 等敏感信息。所有已认证用户均可访问。
     */
    @GetMapping("/chat-models")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> listChatModels() {
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功", "data", providerService.listChatModels()));
    }

    // ==================== Sniff ====================

    @PostMapping("/{id:\\d+}/sniff-models")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:test')")
    public ResponseEntity<Map<String, Object>> sniffModels(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功", "data", providerService.sniffModels(id)));
    }
}
