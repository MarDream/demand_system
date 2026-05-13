package com.demand.system.module.llm.controller;

import com.demand.system.module.llm.constant.LlmModelRole;
import com.demand.system.module.llm.dto.*;
import com.demand.system.module.llm.service.LlmProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/llm-providers")
@RequiredArgsConstructor
public class LlmProviderController {

    private final LlmProviderService providerService;

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
    public ResponseEntity<Map<String, Object>> create(@Validated @RequestBody LlmProviderDTO dto) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "创建成功", "data", providerService.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Validated @RequestBody LlmProviderDTO dto) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功", "data", providerService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        providerService.delete(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    @PatchMapping("/{id}/toggle")
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
    public ResponseEntity<Map<String, Object>> addModel(
            @PathVariable Long id,
            @Validated @RequestBody LlmModelDTO dto) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "创建成功", "data", providerService.addModel(id, dto)));
    }

    @PutMapping("/{id}/models/{modelId}")
    public ResponseEntity<Map<String, Object>> updateModel(
            @PathVariable Long id,
            @PathVariable Long modelId,
            @Validated @RequestBody LlmModelDTO dto) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功", "data", providerService.updateModel(modelId, dto)));
    }

    @DeleteMapping("/{id}/models/{modelId}")
    public ResponseEntity<Map<String, Object>> deleteModel(
            @PathVariable Long id,
            @PathVariable Long modelId) {
        providerService.deleteModel(modelId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    @PatchMapping("/{id}/models/{modelId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleModelEnabled(
            @PathVariable Long id,
            @PathVariable Long modelId) {
        providerService.toggleModelEnabled(modelId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功"));
    }

    @PostMapping("/{id}/models/{modelId}/test")
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
    public ResponseEntity<Map<String, Object>> sniffModels(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功", "data", providerService.sniffModels(id)));
    }
}
