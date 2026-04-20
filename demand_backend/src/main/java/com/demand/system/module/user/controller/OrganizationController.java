package com.demand.system.module.user.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.user.entity.Department;
import com.demand.system.module.user.entity.Position;
import com.demand.system.module.user.entity.Region;
import com.demand.system.module.user.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping("/regions/tree")
    public Result<List<Region>> getRegionTree() {
        return Result.success(organizationService.getRegionTree());
    }

    @PostMapping("/regions")
    public Result<Void> createRegion(@Valid @RequestBody Region region) {
        organizationService.createRegion(region);
        return Result.success();
    }

    @PutMapping("/regions/{id}")
    public Result<Void> updateRegion(@PathVariable Long id, @Valid @RequestBody Region region) {
        region.setId(id);
        organizationService.updateRegion(region);
        return Result.success();
    }

    @GetMapping("/departments/tree")
    public Result<List<Department>> getDepartmentTree() {
        return Result.success(organizationService.getDepartmentTree());
    }

    @PostMapping("/departments")
    public Result<Void> createDepartment(@Valid @RequestBody Department dept) {
        organizationService.createDepartment(dept);
        return Result.success();
    }

    @PutMapping("/departments/{id}")
    public Result<Void> updateDepartment(@PathVariable Long id, @Valid @RequestBody Department dept) {
        dept.setId(id);
        organizationService.updateDepartment(dept);
        return Result.success();
    }

    @GetMapping("/positions")
    public Result<List<Position>> listPositions() {
        return Result.success(organizationService.listPositions());
    }

    @PostMapping("/positions")
    public Result<Void> createPosition(@Valid @RequestBody Position position) {
        organizationService.createPosition(position);
        return Result.success();
    }

    @PutMapping("/positions/{id}")
    public Result<Void> updatePosition(@PathVariable Long id, @Valid @RequestBody Position position) {
        position.setId(id);
        organizationService.updatePosition(position);
        return Result.success();
    }

    @DeleteMapping("/regions/{id}")
    public Result<Void> deleteRegion(@PathVariable Long id) {
        organizationService.deleteRegion(id);
        return Result.success();
    }

    @DeleteMapping("/departments/{id}")
    public Result<Void> deleteDepartment(@PathVariable Long id) {
        organizationService.deleteDepartment(id);
        return Result.success();
    }

    @DeleteMapping("/positions/{id}")
    public Result<Void> deletePosition(@PathVariable Long id) {
        organizationService.deletePosition(id);
        return Result.success();
    }

    @PutMapping("/user-organizations/{userId}")
    public Result<Void> updateOrg(@PathVariable Long userId, @Valid @RequestBody UserOrgDTO dto) {
        organizationService.updateOrg(userId, dto.getRegionId(), dto.getDepartmentId(),
                dto.getPositionId(), dto.getSystemRole());
        return Result.success();
    }

    @Data
    public static class UserOrgDTO {
        private Long regionId;
        private Long departmentId;
        private Long positionId;
        private String systemRole;
    }
}
