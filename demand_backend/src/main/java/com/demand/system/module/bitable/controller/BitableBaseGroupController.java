package com.demand.system.module.bitable.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableBaseGroupCreateDTO;
import com.demand.system.module.bitable.dto.BitableBaseGroupMoveDTO;
import com.demand.system.module.bitable.dto.BitableBaseGroupUpdateDTO;
import com.demand.system.module.bitable.dto.BitableBaseGroupVO;
import com.demand.system.module.bitable.dto.BitableBaseMoveGroupDTO;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableBaseGroupService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多维表格 Base 分组控制器
 * <p>
 * Base 分组为全局维度的目录树，用于多维表格列表页左侧的树形管理
 * （与「数据表分组」「模型应用分组」的方式一致）。
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableBaseGroupController {

    private final BitableBaseGroupService groupService;
    private final BitableAuthorizationService authorizationService;

    public BitableBaseGroupController(BitableBaseGroupService groupService,
                                      BitableAuthorizationService authorizationService) {
        this.groupService = groupService;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询分组树
     */
    @GetMapping("/base-groups")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableBaseGroupVO>> listGroupTree() {
        return Result.success(groupService.listGroupTree());
    }

    /**
     * 新建分组
     */
    @PostMapping("/base-groups")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Long> createGroup(@Valid @RequestBody BitableBaseGroupCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(groupService.createGroup(dto, userId));
    }

    /**
     * 重命名分组
     */
    @PutMapping("/base-groups/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> renameGroup(@PathVariable Long id,
                                    @Valid @RequestBody BitableBaseGroupUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        groupService.renameGroup(id, dto, userId);
        return Result.success();
    }

    /**
     * 移动分组（变更父级 / 同级排序）
     */
    @PutMapping("/base-groups/{id}/move")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> moveGroup(@PathVariable Long id,
                                  @RequestBody BitableBaseGroupMoveDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        groupService.moveGroup(id, dto, userId);
        return Result.success();
    }

    /**
     * 删除分组（子分组与 Base 上移到父级）
     */
    @DeleteMapping("/base-groups/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        groupService.deleteGroup(id, userId);
        return Result.success();
    }

    /**
     * Base 归组（groupId 为 null 表示移出分组）
     */
    @PutMapping("/bases/{id}/group")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> moveBaseToGroup(@PathVariable Long id,
                                        @RequestBody BitableBaseMoveGroupDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(id, userId);
        groupService.moveBaseToGroup(id, dto.getGroupId(), userId);
        return Result.success();
    }
}
