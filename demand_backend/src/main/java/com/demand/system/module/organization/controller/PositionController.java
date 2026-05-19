package com.demand.system.module.organization.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.organization.dto.*;
import com.demand.system.module.organization.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/positions")
@Tag(name = "岗位管理")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping
    @Operation(summary = "获取岗位列表")
    public Result<List<PositionVO>> list() {
        return Result.success(positionService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取岗位详情")
    public Result<PositionVO> getById(@PathVariable Long id) {
        return Result.success(positionService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增岗位")
    public Result<Void> create(@Valid @RequestBody PositionCreateDTO dto) {
        positionService.create(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新岗位")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PositionUpdateDTO dto) {
        dto.setId(id);
        positionService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除岗位")
    public Result<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return Result.success();
    }

    @PutMapping("/sort")
    @Operation(summary = "批量更新排序")
    public Result<Void> updateSort(@Valid @RequestBody PositionSortDTO dto) {
        positionService.updateSort(dto);
        return Result.success();
    }
}
