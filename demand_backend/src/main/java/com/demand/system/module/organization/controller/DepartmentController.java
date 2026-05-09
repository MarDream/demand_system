package com.demand.system.module.organization.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.organization.dto.*;
import com.demand.system.module.organization.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "部门管理")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @Operation(summary = "获取部门列表")
    public Result<PageResult<DepartmentVO>> list(DepartmentQueryDTO query) {
        return Result.success(departmentService.list(query));
    }

    @GetMapping("/tree")
    @Operation(summary = "获取部门树")
    public Result<List<DepartmentVO>> getTree() {
        return Result.success(departmentService.getTree());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取部门详情")
    public Result<DepartmentVO> getById(@PathVariable Long id) {
        return Result.success(departmentService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增部门")
    public Result<Void> create(@Valid @RequestBody DepartmentCreateDTO dto) {
        departmentService.create(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新部门")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DepartmentUpdateDTO dto) {
        dto.setId(id);
        departmentService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除部门")
    public Result<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return Result.success();
    }

    @PutMapping("/sort")
    @Operation(summary = "批量更新排序")
    public Result<Void> updateSort(@Valid @RequestBody DepartmentSortDTO dto) {
        departmentService.updateSort(dto);
        return Result.success();
    }
}
