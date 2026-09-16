package com.demand.system.module.bitable.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableTableGroupCreateDTO;
import com.demand.system.module.bitable.dto.BitableTableGroupMoveDTO;
import com.demand.system.module.bitable.dto.BitableTableGroupUpdateDTO;
import com.demand.system.module.bitable.dto.BitableTableGroupVO;
import com.demand.system.module.bitable.dto.BitableTableMoveGroupDTO;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableTableGroupService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多维表格数据表分组控制器
 * <p>
 * 分组用于编辑器左侧「数据表」侧边栏的目录树管理，支持任意层级嵌套。
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableTableGroupController {

    private final BitableTableGroupService groupService;
    private final BitableAuthorizationService authorizationService;

    public BitableTableGroupController(BitableTableGroupService groupService,
                                       BitableAuthorizationService authorizationService) {
        this.groupService = groupService;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询分组树
     */
    @GetMapping("/bases/{baseId}/table-groups")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableTableGroupVO>> listGroupTree(@PathVariable Long baseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkReadPermission(baseId, userId);
        return Result.success(groupService.listGroupTree(baseId));
    }

    /**
     * 新建分组
     */
    @PostMapping("/bases/{baseId}/table-groups")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createGroup(@PathVariable Long baseId,
                                    @Valid @RequestBody BitableTableGroupCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(baseId, userId);
        return Result.success(groupService.createGroup(baseId, dto, userId));
    }

    /**
     * 重命名分组
     */
    @PutMapping("/table-groups/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> renameGroup(@PathVariable Long id,
                                    @Valid @RequestBody BitableTableGroupUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(requireBaseId(id), userId);
        groupService.renameGroup(id, dto, userId);
        return Result.success();
    }

    /**
     * 移动分组（变更父级 / 同级排序）
     */
    @PutMapping("/table-groups/{id}/move")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> moveGroup(@PathVariable Long id,
                                  @RequestBody BitableTableGroupMoveDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(requireBaseId(id), userId);
        groupService.moveGroup(id, dto, userId);
        return Result.success();
    }

    /**
     * 删除分组（子分组与数据表上移到父级）
     */
    @DeleteMapping("/table-groups/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(requireBaseId(id), userId);
        groupService.deleteGroup(id, userId);
        return Result.success();
    }

    /**
     * 数据表归组（groupId 为 null 表示移出分组）
     */
    @PutMapping("/tables/{id}/group")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> moveTableToGroup(@PathVariable Long id,
                                         @RequestBody BitableTableMoveGroupDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(authorizationService.getBaseIdByTableId(id), userId);
        groupService.moveTableToGroup(id, dto.getGroupId(), userId);
        return Result.success();
    }

    /**
     * 反查分组所属的 Base，用于权限校验
     */
    private Long requireBaseId(Long groupId) {
        Long baseId = groupService.getBaseIdByGroupId(groupId);
        if (baseId == null) {
            throw new BusinessException("分组不存在");
        }
        return baseId;
    }
}
