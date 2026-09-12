package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.FormPublishCreateDTO;
import com.demand.system.module.bitable.dto.FormPublishVO;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableFormPublishService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 多维表格-公开表单发布管理控制器（需登录）
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableFormPublishController {

    private final BitableFormPublishService formPublishService;
    private final BitableAuthorizationService authorizationService;

    public BitableFormPublishController(BitableFormPublishService formPublishService,
                                        BitableAuthorizationService authorizationService) {
        this.formPublishService = formPublishService;
        this.authorizationService = authorizationService;
    }

    /**
     * 发布表单视图（ADMIN 及以上）
     */
    @PostMapping("/tables/{tableId}/views/{viewId}/publish")
    @PreAuthorize("isAuthenticated()")
    public Result<FormPublishVO> publish(@PathVariable Long tableId,
                                         @PathVariable Long viewId,
                                         @Valid @RequestBody FormPublishCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        authorizationService.checkManagePermission(baseId, userId);
        FormPublishVO vo = formPublishService.publish(baseId, tableId, viewId, dto, userId);
        return Result.success(vo);
    }

    /**
     * 查询视图的发布信息
     */
    @GetMapping("/views/{viewId}/publish")
    @PreAuthorize("isAuthenticated()")
    public Result<FormPublishVO> getPublish(@PathVariable Long viewId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByViewId(viewId);
        authorizationService.checkReadPermission(baseId, userId);
        return Result.success(formPublishService.getByViewId(viewId));
    }

    /**
     * 启用/停用公开表单
     */
    @PostMapping("/views/{viewId}/publish/status")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updatePublishStatus(@PathVariable Long viewId,
                                            @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByViewId(viewId);
        authorizationService.checkManagePermission(baseId, userId);
        boolean enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        formPublishService.updateStatus(viewId, enabled);
        return Result.success();
    }
}
