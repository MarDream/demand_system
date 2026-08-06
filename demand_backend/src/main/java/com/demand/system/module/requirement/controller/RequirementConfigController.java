package com.demand.system.module.requirement.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.requirement.dto.RequirementFormConfigDTO;
import com.demand.system.module.requirement.dto.SortRequest;
import com.demand.system.module.requirement.entity.PriorityConfig;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import com.demand.system.module.requirement.service.RequirementConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/requirement-config")
@Tag(name = "需求配置管理", description = "需求类型和优先级配置")
public class RequirementConfigController {

    private final RequirementConfigService configService;

    public RequirementConfigController(RequirementConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取需求类型列表")
    public Result<List<RequirementTypeConfig>> listTypes() {
        return configService.listTypes();
    }

    @GetMapping("/types/available")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取可用需求类型列表（仅已绑定活跃工作流的类型）")
    public Result<List<RequirementTypeConfig>> listAvailableTypes() {
        return configService.listAvailableTypes();
    }

    @PutMapping("/types/{typeCode}/workflow")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:update')")
    @Operation(summary = "绑定/解绑需求类型的工作流版本", description = "传入 workflowVersionId 绑定，传 null 解绑")
    public Result<Void> bindWorkflow(@PathVariable String typeCode,
                                     @RequestParam(required = false) Long workflowVersionId) {
        return configService.bindWorkflow(typeCode, workflowVersionId);
    }

    @PutMapping("/types/{id}/enabled")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:update')")
    @Operation(summary = "启用/禁用需求类型", description = "开启时若绑定工作流已禁用则拒绝")
    public Result<Void> updateTypeEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return configService.updateTypeEnabled(id, enabled);
    }

    @GetMapping("/projects/{projectId}/create-form")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取新建需求表单配置")
    public Result<RequirementFormConfigDTO> getCreateFormConfig(@PathVariable Long projectId) {
        return configService.getCreateFormConfig(projectId);
    }

    @PostMapping("/types")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:create')")
    @Operation(summary = "创建需求类型")
    public Result<Void> createType(@Valid @RequestBody RequirementTypeConfig type) {
        return configService.createType(type);
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:update')")
    @Operation(summary = "更新需求类型")
    public Result<Void> updateType(@PathVariable Long id, @Valid @RequestBody RequirementTypeConfig type) {
        type.setId(id);
        return configService.updateType(type);
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:delete')")
    @Operation(summary = "删除需求类型")
    public Result<Void> deleteType(@PathVariable Long id) {
        return configService.deleteType(id);
    }

    @PostMapping("/types/sort")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:update')")
    @Operation(summary = "需求类型排序", description = "批量更新需求类型的排序顺序")
    public Result<List<RequirementTypeConfig>> sortTypes(@Valid @RequestBody List<SortRequest> sortRequests) {
        return configService.sortTypes(sortRequests);
    }

    @GetMapping("/priorities")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取优先级列表")
    public Result<List<PriorityConfig>> listPriorities() {
        return configService.listPriorities();
    }

    @PostMapping("/priorities")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:create')")
    @Operation(summary = "创建优先级")
    public Result<Void> createPriority(@Valid @RequestBody PriorityConfig priority) {
        return configService.createPriority(priority);
    }

    @PutMapping("/priorities/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:update')")
    @Operation(summary = "更新优先级")
    public Result<Void> updatePriority(@PathVariable Long id, @Valid @RequestBody PriorityConfig priority) {
        priority.setId(id);
        return configService.updatePriority(priority);
    }

    @DeleteMapping("/priorities/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:delete')")
    @Operation(summary = "删除优先级")
    public Result<Void> deletePriority(@PathVariable Long id) {
        return configService.deletePriority(id);
    }

    @PostMapping("/priorities/sort")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-config:update')")
    @Operation(summary = "优先级排序", description = "批量更新优先级的排序顺序")
    public Result<List<PriorityConfig>> sortPriorities(@Valid @RequestBody List<SortRequest> sortRequests) {
        return configService.sortPriorities(sortRequests);
    }
}
