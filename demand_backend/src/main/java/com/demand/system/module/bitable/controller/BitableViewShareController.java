package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.ViewShareCreateDTO;
import com.demand.system.module.bitable.dto.ViewShareVO;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableViewShareService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 多维表格-视图分享管理控制器（需登录）
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableViewShareController {

    private final BitableViewShareService viewShareService;
    private final BitableAuthorizationService authorizationService;

    public BitableViewShareController(BitableViewShareService viewShareService,
                                      BitableAuthorizationService authorizationService) {
        this.viewShareService = viewShareService;
        this.authorizationService = authorizationService;
    }

    /**
     * 创建/更新视图分享（ADMIN 及以上）
     */
    @PostMapping("/views/{viewId}/share")
    @PreAuthorize("isAuthenticated()")
    public Result<ViewShareVO> share(@PathVariable Long viewId,
                                     @Valid @RequestBody ViewShareCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByViewId(viewId);
        authorizationService.checkManagePermission(baseId, userId);
        return Result.success(viewShareService.share(viewId, dto, userId));
    }

    /**
     * 查询视图的分享信息
     */
    @GetMapping("/views/{viewId}/share")
    @PreAuthorize("isAuthenticated()")
    public Result<ViewShareVO> getShare(@PathVariable Long viewId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByViewId(viewId);
        authorizationService.checkReadPermission(baseId, userId);
        return Result.success(viewShareService.getByViewId(viewId));
    }

    /**
     * 启用/停用分享
     */
    @PostMapping("/views/{viewId}/share/status")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateShareStatus(@PathVariable Long viewId,
                                          @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByViewId(viewId);
        authorizationService.checkManagePermission(baseId, userId);
        boolean enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        viewShareService.updateStatus(viewId, enabled);
        return Result.success();
    }
}
