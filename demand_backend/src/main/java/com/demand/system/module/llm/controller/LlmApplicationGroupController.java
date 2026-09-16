package com.demand.system.module.llm.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.llm.dto.LlmApplicationGroupCreateDTO;
import com.demand.system.module.llm.dto.LlmApplicationGroupMoveDTO;
import com.demand.system.module.llm.dto.LlmApplicationGroupUpdateDTO;
import com.demand.system.module.llm.service.LlmApplicationGroupService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * LLM 功能点模型应用分组控制器
 * <p>
 * 分组用于「模型配置 → 模型应用」页左侧目录树，支持任意层级嵌套，
 * 与管理多维表格数据表分组的方式保持一致。
 */
@RestController
@RequestMapping("/api/v1/llm-application-groups")
public class LlmApplicationGroupController {

    private final LlmApplicationGroupService groupService;

    public LlmApplicationGroupController(LlmApplicationGroupService groupService) {
        this.groupService = groupService;
    }

    /**
     * 查询分组树
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<?> tree() {
        return Result.success(groupService.listGroupTree());
    }

    /**
     * 新建分组
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public Result<?> create(@Valid @RequestBody LlmApplicationGroupCreateDTO dto) {
        return Result.success(groupService.createGroup(dto));
    }

    /**
     * 重命名分组
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public Result<?> rename(@PathVariable Long id, @Valid @RequestBody LlmApplicationGroupUpdateDTO dto) {
        groupService.renameGroup(id, dto);
        return Result.success();
    }

    /**
     * 移动分组（变更父级 / 同级排序）
     */
    @PutMapping("/{id}/move")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public Result<?> move(@PathVariable Long id, @RequestBody LlmApplicationGroupMoveDTO dto) {
        groupService.moveGroup(id, dto);
        return Result.success();
    }

    /**
     * 删除分组（子分组与功能点上移到父级）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:llm-provider:update')")
    public Result<?> delete(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return Result.success();
    }
}
