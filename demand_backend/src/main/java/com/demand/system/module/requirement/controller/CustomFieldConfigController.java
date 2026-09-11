package com.demand.system.module.requirement.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.requirement.entity.CustomField;
import com.demand.system.module.requirement.service.CustomFieldConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 需求动态字段定义管理接口。 */
@RestController
@RequestMapping("/api/v1/requirement-config/fields")
@Tag(name = "需求动态字段管理", description = "按需求类型配置动态字段")
public class CustomFieldConfigController {

    private final CustomFieldConfigService fieldConfigService;

    public CustomFieldConfigController(CustomFieldConfigService fieldConfigService) {
        this.fieldConfigService = fieldConfigService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:update')")
    @Operation(summary = "查询某项目/需求类型下的动态字段定义")
    public Result<List<com.demand.system.module.requirement.dto.CustomFieldConfigDTO>> listFields(
            @RequestParam Long projectId,
            @RequestParam(required = false) String typeCode) {
        return fieldConfigService.listFields(projectId, typeCode);
    }

    @GetMapping("/schema")
    @Operation(summary = "按项目+需求类型返回带节点权限的动态字段 schema（创建态取流程首节点权限）")
    public Result<List<com.demand.system.module.requirement.dto.CustomFieldConfigDTO>> buildSchema(
            @RequestParam Long projectId,
            @RequestParam String typeCode) {
        return fieldConfigService.buildCreateSchema(projectId, typeCode);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:create')")
    @Operation(summary = "创建动态字段")
    public Result<Void> createField(@Valid @RequestBody CustomField field) {
        return fieldConfigService.createField(field);
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:update')")
    @Operation(summary = "更新动态字段（仅允许改展示名/选项/必填/默认值/排序/启用状态）")
    public Result<Void> updateField(@Valid @RequestBody CustomField field) {
        return fieldConfigService.updateField(field);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:delete')")
    @Operation(summary = "删除动态字段（软删除）")
    public Result<Void> deleteField(@PathVariable Long id) {
        return fieldConfigService.deleteField(id);
    }

    @PutMapping("/sort")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:update')")
    @Operation(summary = "批量排序动态字段")
    public Result<List<com.demand.system.module.requirement.entity.CustomField>> sortFields(
            @RequestBody List<com.demand.system.module.requirement.dto.SortItemDTO> items) {
        return fieldConfigService.sortFields(items);
    }
}
