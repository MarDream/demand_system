package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.constant.OperationType;
import com.demand.system.module.bitable.dto.BitableLeafMoveRequest;
import com.demand.system.module.bitable.dto.BitableLeafOrderItemDTO;
import com.demand.system.module.bitable.entity.BitableBase;
import com.demand.system.module.bitable.entity.BitableBaseGroup;
import com.demand.system.module.bitable.entity.BitableDashboard;
import com.demand.system.module.bitable.entity.BitableTable;
import com.demand.system.module.bitable.mapper.BitableBaseGroupMapper;
import com.demand.system.module.bitable.mapper.BitableBaseGroupMapper;
import com.demand.system.module.bitable.mapper.BitableBaseMapper;
import com.demand.system.module.bitable.mapper.BitableDashboardMapper;
import com.demand.system.module.bitable.mapper.BitableTableMapper;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableLeafSortService;
import com.demand.system.module.bitable.util.BitableAuditHelper;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 目录树叶子排序与移动：数据表与仪表盘共用同一 sort_order 序列。
 *
 * <p>目录树里两者是同层级的兄弟节点，所以排序必须放在一个接口里做——
 * 分成「数据表排序 + 仪表盘排序」两个接口无法表达交错顺序。</p>
 *
 * <p>叶子的「目录树层级」= 它自己的 base_group_id（独立归属），为 null 时回退
 * 所属 Base 的 group_id。拖拽单个叶子换层走 {@link #moveLeaf}，只动该叶子自身。</p>
 */
@Service
public class BitableLeafSortServiceImpl implements BitableLeafSortService {

    private static final String KIND_TABLE = "table";
    private static final String KIND_DASHBOARD = "dashboard";

    private final BitableBaseMapper baseMapper;
    private final BitableTableMapper tableMapper;
    private final BitableDashboardMapper dashboardMapper;
    private final BitableBaseGroupMapper baseGroupMapper;
    private final BitableAuthorizationService authorizationService;
    private final BitableAuditHelper auditHelper;

    public BitableLeafSortServiceImpl(BitableBaseMapper baseMapper,
                                      BitableTableMapper tableMapper,
                                      BitableDashboardMapper dashboardMapper,
                                      BitableBaseGroupMapper baseGroupMapper,
                                      BitableAuthorizationService authorizationService,
                                      BitableAuditHelper auditHelper) {
        this.baseMapper = baseMapper;
        this.tableMapper = tableMapper;
        this.dashboardMapper = dashboardMapper;
        this.baseGroupMapper = baseGroupMapper;
        this.authorizationService = authorizationService;
        this.auditHelper = auditHelper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortLeaves(Long baseId, List<BitableLeafOrderItemDTO> ordered, Long userId) {
        if (baseId == null) {
            throw new BusinessException("baseId 不能为空");
        }
        if (ordered == null || ordered.isEmpty()) {
            return;
        }
        BitableBase anchor = baseMapper.selectById(baseId);
        if (anchor == null) {
            throw new BusinessException("多维表格不存在");
        }

        // 第一遍：解析每个叶子，收集涉及的 Base
        Set<Long> touchedBaseIds = new LinkedHashSet<>();
        for (BitableLeafOrderItemDTO item : ordered) {
            if (item == null || item.getId() == null) {
                throw new BusinessException("排序项缺少 id");
            }
            touchedBaseIds.add(resolveLeafBaseId(item));
        }

        // 一次拖拽可能横跨同一层级下的多个 Base，逐个校验写权限
        Map<Long, BitableBase> baseCache = new HashMap<>();
        for (Long id : touchedBaseIds) {
            BitableBase base = baseMapper.selectById(id);
            if (base == null) {
                throw new BusinessException("多维表格不存在");
            }
            baseCache.put(id, base);
        }
        // 同一目录树层级 = 全部叶子的有效分组一致（独立归属 base_group_id 优先，回退 Base 分组；
        // NULL 表示未分组）。锚点取第一个叶子的有效分组——叶子可独立于所属 Base 挂在别的层级
        Long anchorGroupId = effectiveGroupOfLeaf(ordered.get(0), baseCache);
        for (BitableLeafOrderItemDTO item : ordered) {
            if (!Objects.equals(effectiveGroupOfLeaf(item, baseCache), anchorGroupId)) {
                throw new BusinessException("只能对同一层级下的节点排序");
            }
        }
        for (Long id : touchedBaseIds) {
            authorizationService.checkWritePermission(id, userId);
        }

        // 第二遍：按下标回写。只 set sort_order 一列，避免覆盖并发修改的其它字段
        int index = 0;
        for (BitableLeafOrderItemDTO item : ordered) {
            if (KIND_TABLE.equals(item.getKind())) {
                UpdateWrapper<BitableTable> wrapper = new UpdateWrapper<>();
                wrapper.eq("id", item.getId()).set("sort_order", index);
                tableMapper.update(null, wrapper);
            } else {
                UpdateWrapper<BitableDashboard> wrapper = new UpdateWrapper<>();
                wrapper.eq("id", item.getId()).set("sort_order", index);
                dashboardMapper.update(null, wrapper);
            }
            index++;
        }
    }

    /** 解析叶子节点，返回它所属的 BaseId；节点不存在直接抛错 */
    private Long resolveLeafBaseId(BitableLeafOrderItemDTO item) {
        if (KIND_TABLE.equals(item.getKind())) {
            BitableTable table = tableMapper.selectById(item.getId());
            if (table == null) {
                throw new BusinessException("数据表不存在");
            }
            return table.getBaseId();
        }
        if (KIND_DASHBOARD.equals(item.getKind())) {
            BitableDashboard dashboard = dashboardMapper.selectById(item.getId());
            if (dashboard == null) {
                throw new BusinessException("仪表盘不存在");
            }
            return dashboard.getBaseId();
        }
        throw new BusinessException("不支持的节点类型: " + item.getKind());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveLeaf(BitableLeafMoveRequest request, Long userId) {
        if (request == null || request.getId() == null) {
            throw new BusinessException("叶子 id 不能为空");
        }
        String kind = request.getKind();
        if (!KIND_TABLE.equals(kind) && !KIND_DASHBOARD.equals(kind)) {
            throw new BusinessException("不支持的节点类型: " + kind);
        }
        Long targetGroupId = request.getTargetGroupId();
        if (targetGroupId != null) {
            BitableBaseGroup target = baseGroupMapper.selectById(targetGroupId);
            if (target == null) {
                throw new BusinessException("目标分组不存在");
            }
        }

        if (KIND_TABLE.equals(kind)) {
            BitableTable table = tableMapper.selectById(request.getId());
            if (table == null) {
                throw new BusinessException("数据表不存在");
            }
            Long beforeGroupId = effectiveGroupId(table.getBaseGroupId(), table.getBaseId());
            Long afterGroupId = targetGroupId;
            if (Objects.equals(beforeGroupId, afterGroupId)) {
                return; // 原地拖回当前层级，无需任何写操作
            }
            // 叶子跨层级移动时，所属 Base 至少要同时出现在两层（读权限兜底）；
            // 移动本身要求源层与目标层的写权限（这里以 Base 写权限近似，Base 归属变更仍走 moveBaseToGroup）
            authorizationService.checkWritePermission(table.getBaseId(), userId);

            UpdateWrapper<BitableTable> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", table.getId());
            setTargetGroup(wrapper, targetGroupId);
            // 排到目标层级末尾，避免挤进现有顺序中间
            Integer max = tableMapper.selectMaxSortOrderInGroup(targetGroupId);
            Integer maxDashboard = dashboardMapper.selectMaxSortOrderInGroup(targetGroupId);
            if (maxDashboard != null && (max == null || maxDashboard > max)) {
                max = maxDashboard;
            }
            wrapper.set("sort_order", max == null ? 0 : max + 1);
            tableMapper.update(null, wrapper);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("tableId", table.getId());
            detail.put("beforeGroupId", beforeGroupId);
            detail.put("afterGroupId", afterGroupId);
            auditHelper.record(table.getBaseId(), table.getId(), userId,
                    OperationType.MOVE_TABLE_TO_GROUP, BitableJsonUtils.toJsonString(detail));
            return;
        }

        BitableDashboard dashboard = dashboardMapper.selectById(request.getId());
        if (dashboard == null) {
            throw new BusinessException("仪表盘不存在");
        }
        Long beforeGroupId = effectiveGroupId(dashboard.getBaseGroupId(), dashboard.getBaseId());
        if (Objects.equals(beforeGroupId, targetGroupId)) {
            return;
        }
        authorizationService.checkWritePermission(dashboard.getBaseId(), userId);

        UpdateWrapper<BitableDashboard> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", dashboard.getId());
        if (targetGroupId == null) {
            wrapper.setSql("base_group_id = NULL");
        } else {
            wrapper.set("base_group_id", targetGroupId);
        }
        Integer max = tableMapper.selectMaxSortOrderInGroup(targetGroupId);
        Integer maxDashboard = dashboardMapper.selectMaxSortOrderInGroup(targetGroupId);
        if (maxDashboard != null && (max == null || maxDashboard > max)) {
            max = maxDashboard;
        }
        wrapper.set("sort_order", max == null ? 0 : max + 1);
        dashboardMapper.update(null, wrapper);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("dashboardId", dashboard.getId());
        detail.put("beforeGroupId", beforeGroupId);
        detail.put("afterGroupId", targetGroupId);
        auditHelper.record(dashboard.getBaseId(), dashboard.getId(), userId,
                OperationType.MOVE_TABLE_TO_GROUP, BitableJsonUtils.toJsonString(detail));
    }

    /** 叶子的有效分组：独立归属优先，否则回退所属 Base 的分组 */
    private Long effectiveGroupId(Long leafGroupId, Long baseId) {
        if (leafGroupId != null) {
            return leafGroupId;
        }
        BitableBase base = baseMapper.selectById(baseId);
        return base == null ? null : base.getGroupId();
    }

    /** 排序场景下叶子的有效分组（Base 已在 baseCache 中） */
    private Long effectiveGroupOfLeaf(BitableLeafOrderItemDTO item, Map<Long, BitableBase> baseCache) {
        if (KIND_TABLE.equals(item.getKind())) {
            BitableTable table = tableMapper.selectById(item.getId());
            if (table == null) {
                throw new BusinessException("数据表不存在");
            }
            BitableBase base = baseCache.get(table.getBaseId());
            return table.getBaseGroupId() != null
                    ? table.getBaseGroupId()
                    : (base == null ? null : base.getGroupId());
        }
        BitableDashboard dashboard = dashboardMapper.selectById(item.getId());
        if (dashboard == null) {
            throw new BusinessException("仪表盘不存在");
        }
        BitableBase base = baseCache.get(dashboard.getBaseId());
        return dashboard.getBaseGroupId() != null
                ? dashboard.getBaseGroupId()
                : (base == null ? null : base.getGroupId());
    }

    /** 目标分组写入：null 走 setSql 置 NULL，避免 MP 把 null 当「不更新」 */
    private void setTargetGroup(UpdateWrapper<BitableTable> wrapper, Long targetGroupId) {
        if (targetGroupId == null) {
            wrapper.setSql("base_group_id = NULL");
        } else {
            wrapper.set("base_group_id", targetGroupId);
        }
    }

    @Override
    public int nextSortOrder(Long baseId) {
        BitableBase base = baseMapper.selectById(baseId);
        Long groupId = base == null ? null : base.getGroupId();
        Integer maxTable = tableMapper.selectMaxSortOrderInGroup(groupId);
        Integer maxDashboard = dashboardMapper.selectMaxSortOrderInGroup(groupId);
        int max = 0;
        if (maxTable != null) {
            max = Math.max(max, maxTable);
        }
        if (maxDashboard != null) {
            max = Math.max(max, maxDashboard);
        }
        return max + 1;
    }
}
