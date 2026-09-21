package com.demand.system.module.bitable.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableLeafMoveRequest;
import com.demand.system.module.bitable.dto.BitableLeafSortRequest;
import com.demand.system.module.bitable.service.BitableLeafSortService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多维表格目录树-叶子节点排序与移动控制器。
 *
 * <p>数据表与仪表盘在目录树里是同层级的兄弟节点，共用同一个 sort_order 序列，
 * 所以排序放在一个接口里做，而不是拆成「数据表排序 + 仪表盘排序」两个接口——
 * 后者无法表达两种节点交错的顺序。</p>
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableLeafSortController {

    private final BitableLeafSortService leafSortService;

    public BitableLeafSortController(BitableLeafSortService leafSortService) {
        this.leafSortService = leafSortService;
    }

    /**
     * 同级排序（拖拽排序）：按 leaves 的传入顺序以下标回写 sort_order。
     */
    @PutMapping("/leaves/sort")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> sortLeaves(@RequestBody BitableLeafSortRequest request) {
        if (request == null || request.getBaseId() == null) {
            throw new BusinessException("baseId 不能为空");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        leafSortService.sortLeaves(request.getBaseId(), request.getLeaves(), userId);
        return Result.success();
    }

    /**
     * 跨层级移动（拖拽移动）：把单个叶子（数据表 / 仪表盘）移到目标 Base 分组。
     *
     * <p>只移动该叶子自身，不影响同 Base 下的其它叶子——
     * 旧逻辑拖表会连带整个 Base 挪走，就是把「移动叶子」误发成了「移动 Base」。</p>
     */
    @PutMapping("/leaves/move")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> moveLeaf(@RequestBody BitableLeafMoveRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        leafSortService.moveLeaf(request, userId);
        return Result.success();
    }
}
