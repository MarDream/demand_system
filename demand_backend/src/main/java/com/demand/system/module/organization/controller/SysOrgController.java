package com.demand.system.module.organization.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.organization.dto.*;
import com.demand.system.module.organization.service.SysOrgService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/org")
public class SysOrgController {

    private final SysOrgService sysOrgService;

    public SysOrgController(SysOrgService sysOrgService) {
        this.sysOrgService = sysOrgService;
    }

    @GetMapping("/tree")
    @PreAuthorize("isAuthenticated()")
    public Result<List<SysOrgVO>> getTree() {
        return Result.success(sysOrgService.getTree());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<SysOrgVO> getDetail(@PathVariable Long id) {
        return Result.success(sysOrgService.getDetail(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:org:create')")
    public Result<Void> create(@Valid @RequestBody SysOrgCreateDTO dto) {
        sysOrgService.create(dto);
        return Result.success(null);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:org:update')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysOrgUpdateDTO dto) {
        dto.setId(id);
        sysOrgService.update(dto);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:org:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        sysOrgService.delete(id);
        return Result.success(null);
    }

    @PutMapping("/move")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:org:update')")
    public Result<Void> move(@Valid @RequestBody SysOrgMoveDTO dto) {
        sysOrgService.move(dto);
        return Result.success(null);
    }
}
