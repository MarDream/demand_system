package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.entity.BitableDashboard;
import com.demand.system.module.bitable.entity.BitableDashboardWidget;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    public BitableDashboardController(BitableDashboardService dashboardService,
                                      BitableAuthorizationService authorizationService) {
        this.dashboardService = dashboardService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/bases/{baseId}/dashboards")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableDashboard>> listDashboards(@PathVariable Long baseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkReadPermission(baseId, userId);
        return Result.success(dashboardService.listByBase(baseId));
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
        dashboardService.saveWidgets(id, widgets);
        return Result.success();
    }

    /**
     * 获取仪表盘全部组件的聚合数据（权限跟随当前用户）
     */
    @GetMapping("/dashboards/{id}/data")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> getDashboardData(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireRead(id, userId);
        return Result.success(dashboardService.getDashboardData(id));
    }

    private void requireRead(Long dashboardId, Long userId) {
        Long baseId = authorizationService.getBaseIdByDashboardId(dashboardId);
        authorizationService.checkReadPermission(baseId, userId);
    }

    private void requireWrite(Long dashboardId, Long userId) {
        Long baseId = authorizationService.getBaseIdByDashboardId(dashboardId);
        authorizationService.checkWritePermission(baseId, userId);
    }

    private void requireManage(Long dashboardId, Long userId) {
        Long baseId = authorizationService.getBaseIdByDashboardId(dashboardId);
        authorizationService.checkManagePermission(baseId, userId);
    }
}
