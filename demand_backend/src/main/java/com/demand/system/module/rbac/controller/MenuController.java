package com.demand.system.module.rbac.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.rbac.dto.MenuCreateDTO;
import com.demand.system.module.rbac.dto.MenuSortItem;
import com.demand.system.module.rbac.dto.MenuUpdateDTO;
import com.demand.system.module.rbac.dto.MenuVO;
import com.demand.system.module.rbac.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "菜单管理", description = "RBAC菜单管理接口")
@RestController
@RequestMapping("/api/v1/rbac/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @Operation(summary = "查询全部菜单树")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<List<MenuVO>> listAllMenus() {
        return menuService.listAllMenus();
    }

    @Operation(summary = "查询当前用户可见菜单树")
    @GetMapping("/current")
    public Result<List<MenuVO>> listCurrentUserMenus() {
        return menuService.listCurrentUserMenus();
    }

    @Operation(summary = "查询菜单详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<MenuVO> getMenu(@PathVariable Long id) {
        return menuService.getMenu(id);
    }

    @Operation(summary = "创建菜单")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> createMenu(@Valid @RequestBody MenuCreateDTO request) {
        return menuService.createMenu(request);
    }

    @Operation(summary = "更新菜单")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> updateMenu(@PathVariable Long id, @Valid @RequestBody MenuUpdateDTO request) {
        request.setId(id);
        return menuService.updateMenu(request);
    }

    @Operation(summary = "删除菜单")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> deleteMenu(@PathVariable Long id) {
        return menuService.deleteMenu(id);
    }

    @Operation(summary = "批量排序菜单")
    @PutMapping("/batch-sort")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> batchSort(@RequestBody List<MenuSortItem> items) {
        return menuService.batchSort(items);
    }
}
