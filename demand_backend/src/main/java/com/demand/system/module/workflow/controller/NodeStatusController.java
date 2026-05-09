package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.entity.NodeStatus;
import com.demand.system.module.workflow.service.NodeStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/node-statuses")
@RequiredArgsConstructor
@Tag(name = "节点状态管理", description = "全局节点状态字典管理")
public class NodeStatusController {

    private final NodeStatusService nodeStatusService;

    @GetMapping
    @Operation(summary = "获取所有节点状态")
    public Result<List<NodeStatus>> list() {
        return Result.success(nodeStatusService.list());
    }

    @PostMapping
    @Operation(summary = "创建节点状态")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> create(@Valid @RequestBody NodeStatus nodeStatus) {
        nodeStatusService.create(nodeStatus);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新节点状态")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody NodeStatus nodeStatus) {
        nodeStatus.setId(id);
        nodeStatusService.update(nodeStatus);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除节点状态")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        nodeStatusService.delete(id);
        return Result.success();
    }
}
