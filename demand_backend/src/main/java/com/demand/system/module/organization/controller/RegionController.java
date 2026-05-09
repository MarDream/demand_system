package com.demand.system.module.organization.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.organization.dto.RegionCreateDTO;
import com.demand.system.module.organization.dto.RegionSortDTO;
import com.demand.system.module.organization.dto.RegionUpdateDTO;
import com.demand.system.module.organization.dto.RegionVO;
import com.demand.system.module.organization.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
@Tag(name = "区域管理")
public class RegionController {

    private final RegionService regionService;

    @GetMapping("/tree")
    @Operation(summary = "获取区域树")
    public Result<List<RegionVO>> getTree() {
        return Result.success(regionService.getTree());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取区域详情")
    public Result<RegionVO> getById(@PathVariable Long id) {
        return Result.success(regionService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增区域")
    public Result<Void> create(@Valid @RequestBody RegionCreateDTO dto) {
        regionService.create(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新区域")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RegionUpdateDTO dto) {
        dto.setId(id);
        regionService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除区域")
    public Result<Void> delete(@PathVariable Long id) {
        regionService.delete(id);
        return Result.success();
    }

    @PutMapping("/sort")
    @Operation(summary = "批量更新排序")
    public Result<Void> updateSort(@Valid @RequestBody RegionSortDTO dto) {
        regionService.updateSort(dto);
        return Result.success();
    }
}
