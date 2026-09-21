package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.entity.BitableDashboard;
import com.demand.system.module.bitable.entity.BitableDashboardWidget;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableBaseRoleService;
import com.demand.system.module.bitable.service.BitableDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多维表格-仪表盘控制器
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableDashboardController {

    private final BitableDashboardService dashboardService;
    private final BitableAuthorizationService authorizationService;
    private final BitableBaseRoleService roleService;

    public BitableDashboardController(BitableDashboardService dashboardService,
                                      BitableAuthorizationService authorizationService,
                                      BitableBaseRoleService roleService) {
        this.dashboardService = dashboardService;
        this.authorizationService = authorizationService;
        this.roleService = roleService;
    }

    @GetMapping("/bases/{baseId}/dashboards")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableDashboard>> listDashboards(@PathVariable Long baseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkReadPermission(baseId, userId);
        return Result.success(dashboardService.listByBase(baseId));
    }

    /**
     * 批量列出多个 Base 的仪表盘（外层目录树展示用，仅返回 id/baseId/name 轻量字段）
     */
    @GetMapping("/bases/dashboards/batch")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> listDashboardsBatch(@RequestParam("baseIds") List<Long> baseIds) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (baseIds.size() > 100) {
            baseIds = baseIds.subList(0, 100);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long baseId : baseIds) {
            try {
                authorizationService.checkReadPermission(baseId, userId);
            } catch (Exception e) {
                continue;
            }
            for (BitableDashboard dashboard : dashboardService.listByBase(baseId)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", dashboard.getId());
                item.put("baseId", dashboard.getBaseId());
                item.put("name", dashboard.getName());
                // 目录树要按这个字段把仪表盘与数据表混排，漏了会全部退化成 0 而丢掉顺序
                item.put("sortOrder", dashboard.getSortOrder());
                // 独立分组归属（null=跟随所属 Base 分组），树渲染用它判定节点所在层级
                item.put("baseGroupId", dashboard.getBaseGroupId());
                result.add(item);
            }
        }
        return Result.success(result);
    }

    @PostMapping("/bases/{baseId}/dashboards")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createDashboard(@PathVariable Long baseId,
                                        @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkWritePermission(baseId, userId);
        String name = body.get("name") != null ? String.valueOf(body.get("name")) : null;
        return Result.success(dashboardService.create(baseId, name, userId));
    }

    /**
     * AI 一键生成仪表盘：基于 Base 下数据表结构由 LLM 设计组件与布局
     */
    @PostMapping("/bases/{baseId}/dashboards/ai-generate")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> aiGenerateDashboard(@PathVariable Long baseId,
                                            @RequestBody(required = false) Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkWritePermission(baseId, userId);
        String description = body != null && body.get("description") != null
                ? String.valueOf(body.get("description"))
                : null;
        return Result.success(dashboardService.aiGenerate(baseId, description, userId));
    }

    @PutMapping("/dashboards/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> renameDashboard(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireManage(id, userId);
        dashboardService.rename(id, body.get("name") != null ? String.valueOf(body.get("name")) : "");
        return Result.success();
    }

    @DeleteMapping("/dashboards/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteDashboard(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireManage(id, userId);
        dashboardService.delete(id);
        return Result.success();
    }

    @GetMapping("/dashboards/{id}/widgets")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableDashboardWidget>> listWidgets(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireRead(id, userId);
        return Result.success(dashboardService.listWidgets(id));
    }

    @PostMapping("/dashboards/{id}/widgets")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> saveWidgets(@PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireWrite(id, userId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) body.get("widgets");
        dashboardService.saveWidgets(id, widgets, body.get("layoutConfig"));
        return Result.success();
    }

    /**
     * 获取仪表盘全部组件的聚合数据（按仪表盘数据权限与访问者角色裁剪）：
     * none=有不可查看数据时该图表标记 hidden；view=受限表的记录不参与聚合；full=全部数据
     */
    @GetMapping("/dashboards/{id}/data")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> getDashboardData(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireRead(id, userId);
        return Result.success(dashboardService.getDashboardData(id, userId));
    }

    private void requireRead(Long dashboardId, Long userId) {
        Long baseId = authorizationService.getBaseIdByDashboardId(dashboardId);
        authorizationService.checkReadPermission(baseId, userId);
        // 仪表盘整体权限：none=无任何权限，view=仅可查看
        if ("none".equals(roleService.resolveDashboardPermission(baseId, dashboardId, userId))) {
            throw new com.demand.system.common.exception.BusinessException(
                    com.demand.system.common.result.ErrorCode.FORBIDDEN, "当前角色对该仪表盘无任何权限");
        }
    }

    private void requireWrite(Long dashboardId, Long userId) {
        Long baseId = authorizationService.getBaseIdByDashboardId(dashboardId);
        authorizationService.checkWritePermission(baseId, userId);
        // 仪表盘整体权限：仅完全权限可修改结构/组件
        if (!"full".equals(roleService.resolveDashboardPermission(baseId, dashboardId, userId))) {
            throw new com.demand.system.common.exception.BusinessException(
                    com.demand.system.common.result.ErrorCode.FORBIDDEN, "当前角色对该仪表盘仅有「可查看」权限");
        }
    }

    private void requireManage(Long dashboardId, Long userId) {
        Long baseId = authorizationService.getBaseIdByDashboardId(dashboardId);
        authorizationService.checkManagePermission(baseId, userId);
    }
}
