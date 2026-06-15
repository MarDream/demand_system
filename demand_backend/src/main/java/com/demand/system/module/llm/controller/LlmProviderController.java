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
@PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
public class LlmProviderController {
    private final LlmProviderService providerService;

    public LlmProviderController(LlmProviderService providerService) {
        this.providerService = providerService;
    }

    // ==================== Provider ====================

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功", "data", providerService.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功", "data", providerService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:create')")
    public ResponseEntity<Map<String, Object>> create(@Validated @RequestBody LlmProviderDTO dto) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "创建成功", "data", providerService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Validated @RequestBody LlmProviderDTO dto) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功", "data", providerService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:delete')")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        providerService.delete(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public ResponseEntity<Map<String, Object>> toggleEnabled(@PathVariable Long id) {
        providerService.toggleEnabled(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功"));
    }

    @GetMapping("/{id}/api-key")
    public ResponseEntity<Map<String, Object>> getApiKey(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功", "data", Map.of("apiKey", providerService.getApiKey(id))));
    }

    // ==================== Model ====================

    @PostMapping("/{id}/models")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:create')")
    public ResponseEntity<Map<String, Object>> addModel(
            @PathVariable Long id,
            @Validated @RequestBody LlmModelDTO dto) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "创建成功", "data", providerService.addModel(id, dto)));
    }

    @PutMapping("/{id}/models/{modelId}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public ResponseEntity<Map<String, Object>> updateModel(
            @PathVariable Long id,
            @PathVariable Long modelId,
            @Validated @RequestBody LlmModelDTO dto) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功", "data", providerService.updateModel(modelId, dto)));
    }

    @DeleteMapping("/{id}/models/{modelId}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:delete')")
    public ResponseEntity<Map<String, Object>> deleteModel(
            @PathVariable Long id,
            @PathVariable Long modelId) {
        providerService.deleteModel(modelId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    @PatchMapping("/{id}/models/{modelId}/toggle")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public ResponseEntity<Map<String, Object>> toggleModelEnabled(
            @PathVariable Long id,
            @PathVariable Long modelId) {
        providerService.toggleModelEnabled(modelId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功"));
    }

    @PostMapping("/{id}/models/{modelId}/test")
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
    public ResponseEntity<Map<String, Object>> getRoles() {
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功", "data", LlmModelRole.PRESET_ROLES));
    }

    // ==================== Sniff ====================

    @PostMapping("/{id}/sniff-models")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:test')")
    public ResponseEntity<Map<String, Object>> sniffModels(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功", "data", providerService.sniffModels(id)));
    }
}
