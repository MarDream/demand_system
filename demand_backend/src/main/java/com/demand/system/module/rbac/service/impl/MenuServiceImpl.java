package com.demand.system.module.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.rbac.dto.MenuCreateDTO;
import com.demand.system.module.rbac.dto.MenuUpdateDTO;
import com.demand.system.module.rbac.dto.MenuSortItem;
import com.demand.system.module.rbac.dto.MenuVO;
import com.demand.system.module.rbac.entity.SysMenu;
import com.demand.system.module.rbac.mapper.SysMenuMapper;
import com.demand.system.module.rbac.service.MenuService;
import com.demand.system.module.rbac.support.RbacPermissionResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class MenuServiceImpl implements MenuService {

    private final SysMenuMapper sysMenuMapper;
    private final RbacPermissionResolver rbacPermissionResolver;

    public MenuServiceImpl(SysMenuMapper sysMenuMapper, RbacPermissionResolver rbacPermissionResolver) {
        this.sysMenuMapper = sysMenuMapper;
        this.rbacPermissionResolver = rbacPermissionResolver;
    }

    @Override
    public Result<List<MenuVO>> listAllMenus() {
        requireMenuManagement();
        return Result.success(buildTree(listAllEnabledMenus(), null));
    }

    @Override
    public Result<List<MenuVO>> listCurrentUserMenus() {
        Long userId = requireCurrentUserId();
        List<String> roles = rbacPermissionResolver.resolveRoles(userId);
        Set<String> permissions = new LinkedHashSet<>(rbacPermissionResolver.resolvePermissions(userId, roles));
        return Result.success(buildTree(listAllEnabledMenus(), permissions));
    }

    @Override
    public Result<MenuVO> getMenu(Long id) {
        requireMenuManagement();
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        return Result.success(toVO(menu));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> createMenu(MenuCreateDTO request) {
        requireMenuManagement();
        validatePermissionCodeUnique(null, request.getPermissionCode());
        SysMenu menu = new SysMenu();
        apply(menu, request);
        LocalDateTime now = LocalDateTime.now();
        menu.setCreatedAt(now);
        menu.setUpdatedAt(now);
        sysMenuMapper.insert(menu);
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateMenu(MenuUpdateDTO request) {
        requireMenuManagement();
        SysMenu menu = sysMenuMapper.selectById(request.getId());
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        String nextPermissionCode = trimToNull(request.getPermissionCode());
        if (!Objects.equals(trimToNull(menu.getPermissionCode()), nextPermissionCode)) {
            validatePermissionCodeUnique(request.getId(), nextPermissionCode);
        }
        apply(menu, request);
        menu.setUpdatedAt(LocalDateTime.now());
        sysMenuMapper.updateById(menu);
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteMenu(Long id) {
        requireMenuManagement();
        long childCount = sysMenuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("请先删除子菜单或按钮");
        }
        if (sysMenuMapper.deleteById(id) == 0) {
            throw new BusinessException("菜单不存在");
        }
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchSort(List<MenuSortItem> items) {
        requireMenuManagement();
        for (MenuSortItem item : items) {
            SysMenu menu = sysMenuMapper.selectById(item.getId());
            if (menu == null) {
                continue;
            }
            menu.setParentId(item.getParentId() == null ? 0L : item.getParentId());
            menu.setSortOrder(item.getSortOrder() == null ? 0 : item.getSortOrder());
            menu.setUpdatedAt(LocalDateTime.now());
            sysMenuMapper.updateById(menu);
        }
        return Result.success();
    }

    private List<SysMenu> listAllEnabledMenus() {
        return sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getEnabled, 1)
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
    }

    private List<MenuVO> buildTree(List<SysMenu> menus, Set<String> permissions) {
        Map<Long, MenuVO> nodeMap = new LinkedHashMap<>();
        List<MenuVO> roots = new ArrayList<>();
        for (SysMenu menu : menus) {
            if (!shouldInclude(menu, permissions)) {
                continue;
            }
            nodeMap.put(menu.getId(), toVO(menu));
        }
        for (MenuVO menu : nodeMap.values()) {
            if (menu.getParentId() == null || menu.getParentId() == 0 || !nodeMap.containsKey(menu.getParentId())) {
                roots.add(menu);
                continue;
            }
            nodeMap.get(menu.getParentId()).getChildren().add(menu);
        }
        return roots;
    }

    private boolean shouldInclude(SysMenu menu, Set<String> permissions) {
        if (menu.getVisible() != null && menu.getVisible() == 0) {
            return false;
        }
        if (permissions == null) {
            return true;
        }
        if (!StringUtils.hasText(menu.getPermissionCode())) {
            return true;
        }
        return permissions.contains(menu.getPermissionCode());
    }

    private MenuVO toVO(SysMenu menu) {
        MenuVO vo = new MenuVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setName(menu.getName());
        vo.setMenuType(menu.getMenuType());
        vo.setPath(menu.getPath());
        vo.setRouteName(menu.getRouteName());
        vo.setComponent(menu.getComponent());
        vo.setIcon(menu.getIcon());
        vo.setSortOrder(menu.getSortOrder());
        vo.setPermissionCode(menu.getPermissionCode());
        vo.setVisible(menu.getVisible());
        vo.setEnabled(menu.getEnabled());
        vo.setKeepAlive(menu.getKeepAlive());
        vo.setRemark(menu.getRemark());
        vo.setCreatedAt(menu.getCreatedAt());
        vo.setUpdatedAt(menu.getUpdatedAt());
        return vo;
    }

    private void apply(SysMenu menu, MenuCreateDTO request) {
        validateParent(request.getParentId(), request.getMenuType());
        menu.setParentId(normalizeParentId(request.getParentId()));
        menu.setName(request.getName());
        menu.setMenuType(request.getMenuType());
        menu.setPath(trimToNull(request.getPath()));
        menu.setRouteName(trimToNull(request.getRouteName()));
        menu.setComponent(trimToNull(request.getComponent()));
        menu.setIcon(trimToNull(request.getIcon()));
        menu.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        menu.setPermissionCode(trimToNull(request.getPermissionCode()));
        menu.setVisible(request.getVisible() == null ? 1 : request.getVisible());
        menu.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        menu.setKeepAlive(request.getKeepAlive() == null ? 0 : request.getKeepAlive());
        menu.setRemark(trimToNull(request.getRemark()));
    }

    private void validateParent(Long parentId, String menuType) {
        if (parentId == null || parentId == 0) {
            return;
        }
        SysMenu parent = sysMenuMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException("父级菜单不存在");
        }
        if (Objects.equals("BUTTON", parent.getMenuType())) {
            throw new BusinessException("按钮下不能再创建子节点");
        }
        if (Objects.equals("DIRECTORY", menuType) && Objects.equals("MENU", parent.getMenuType())) {
            throw new BusinessException("页面菜单下不能创建目录");
        }
    }

    private void validatePermissionCodeUnique(Long id, String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            return;
        }
        List<SysMenu> exists = sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPermissionCode, permissionCode));
        boolean conflict = exists.stream().anyMatch(item -> !Objects.equals(item.getId(), id));
        if (conflict) {
            throw new BusinessException("权限编码已存在");
        }
    }

    private void requireMenuManagement() {
        Long userId = requireCurrentUserId();
        List<String> roles = rbacPermissionResolver.resolveRoles(userId);
        Set<String> permissions = new LinkedHashSet<>(rbacPermissionResolver.resolvePermissions(userId, roles));
        if (rbacPermissionResolver.isSuperAdmin(roles)) {
            return;
        }
        if (!permissions.contains("menu:menu-management") && !permissions.contains("button:menu:grant")) {
            throw new BusinessException("无权限执行菜单管理操作");
        }
    }

    private Long requireCurrentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未获取到当前用户");
        }
        return userId;
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null ? 0L : parentId;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
