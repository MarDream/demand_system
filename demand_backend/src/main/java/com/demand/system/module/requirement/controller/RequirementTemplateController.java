package com.demand.system.module.requirement.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.requirement.dto.RequirementTemplateSaveDTO;
import com.demand.system.module.requirement.dto.RequirementTemplateVO;
import com.demand.system.module.requirement.service.RequirementTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "需求模板管理", description = "需求模板的增删改查接口")
@RestController
@RequestMapping("/api/v1/requirement/templates")
public class RequirementTemplateController {

    private final RequirementTemplateService templateService;

    public RequirementTemplateController(RequirementTemplateService templateService) {
        this.templateService = templateService;
    }

    @Operation(summary = "根据需求类型获取默认模板", description = "根据需求类型编码获取对应的默认模板，所有用户可访问")
    @GetMapping("/by-type")
    public Result<RequirementTemplateVO> getTemplateByType(
        @Parameter(description = "需求类型编码", required = true, example = "FEATURE")
        @RequestParam String typeCode) {
        return Result.success(templateService.getTemplateByType(typeCode));
    }

    @Operation(summary = "根据需求类型获取所有启用模板", description = "获取某类型下所有启用的模板列表，用于创建需求时选择，所有用户可访问")
    @GetMapping("/by-type-list")
    public Result<List<RequirementTemplateVO>> getTemplatesByType(
        @Parameter(description = "需求类型编码", required = true, example = "FEATURE")
        @RequestParam String typeCode) {
        return Result.success(templateService.getTemplatesByType(typeCode));
    }

    @Operation(summary = "获取所有模板列表", description = "获取系统中所有的需求模板，所有用户可访问")
    @GetMapping("/list")
    public Result<List<RequirementTemplateVO>> getAllTemplates() {
        return Result.success(templateService.getAllTemplates());
    }

    @Operation(summary = "保存或更新模板", description = "创建新模板或更新已有模板，需要管理员权限")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-template:update')")
    @PostMapping("/save")
    public Result<Void> saveTemplate(
        @Parameter(description = "模板保存数据", required = true)
        @Valid @RequestBody RequirementTemplateSaveDTO dto) {
        templateService.saveTemplate(dto);
        return Result.success();
    }

    @Operation(summary = "删除模板", description = "软删除指定的需求模板，需要管理员权限")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-template:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> deleteTemplate(
        @Parameter(description = "模板ID", required = true, example = "1")
        @PathVariable Long id) {
        templateService.deleteTemplate(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用模板", description = "切换模板的启用状态，需要管理员权限")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-template:toggle')")
    @PutMapping("/{id}/status")
    public Result<Void> toggleTemplateStatus(
        @Parameter(description = "模板ID", required = true, example = "1")
        @PathVariable Long id,
        @Parameter(description = "状态值：0=禁用，1=启用", required = true, example = "1")
        @RequestParam Integer isActive) {
        templateService.toggleTemplateStatus(id, isActive);
        return Result.success();
    }

    @Operation(summary = "设为默认模板", description = "将指定模板设为该类型的默认模板，需要管理员权限")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:requirement-template:update')")
    @PutMapping("/{id}/default")
    public Result<Void> setDefaultTemplate(
        @Parameter(description = "模板ID", required = true, example = "1")
        @PathVariable Long id) {
        templateService.setDefaultTemplate(id);
        return Result.success();
    }
}
