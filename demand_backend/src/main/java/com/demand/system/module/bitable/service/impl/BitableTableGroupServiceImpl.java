package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.constant.OperationType;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableTableGroupCreateDTO;
import com.demand.system.module.bitable.dto.BitableTableGroupMoveDTO;
import com.demand.system.module.bitable.dto.BitableTableGroupUpdateDTO;
import com.demand.system.module.bitable.dto.BitableTableGroupVO;
import com.demand.system.module.bitable.entity.BitableTable;
import com.demand.system.module.bitable.entity.BitableTableGroup;
import com.demand.system.module.bitable.mapper.BitableTableGroupMapper;
import com.demand.system.module.bitable.mapper.BitableTableMapper;
import com.demand.system.module.bitable.service.BitableTableGroupService;
import com.demand.system.module.bitable.util.BitableAuditHelper;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 多维表格-数据表分组 Service 实现
 */
@Service
public class BitableTableGroupServiceImpl implements BitableTableGroupService {

    private final BitableTableGroupMapper groupMapper;
    private final BitableTableMapper tableMapper;
    private final BitableConverter converter;
    private final BitableAuditHelper auditHelper;

    public BitableTableGroupServiceImpl(BitableTableGroupMapper groupMapper,
                                        BitableTableMapper tableMapper,
                                        BitableConverter converter,
                                        BitableAuditHelper auditHelper) {
        this.groupMapper = groupMapper;
        this.tableMapper = tableMapper;
        this.converter = converter;
        this.auditHelper = auditHelper;
    }

    @Override
    public List<BitableTableGroupVO> listGroupTree(Long baseId) {
        List<BitableTableGroup> groups = groupMapper.selectByBaseId(baseId);
        if (groups.isEmpty()) {
            return new ArrayList<>();
        }
        List<BitableTable> tables = tableMapper.selectByBaseId(baseId);

        // 每个分组「直接挂载」的数据表数量
        Map<Long, Integer> directCount = new HashMap<>();
        for (BitableTable table : tables) {
            if (table.getGroupId() != null) {
                directCount.merge(table.getGroupId(), 1, Integer::sum);
            }
        }

        Map<Long, BitableTableGroupVO> nodeMap = new LinkedHashMap<>();
        for (BitableTableGroup group : groups) {
            BitableTableGroupVO vo = converter.toTableGroupVO(group);
            int count = directCount.getOrDefault(group.getId(), 0);
            vo.setTableCount(count);
            vo.setTotalTableCount(count);
            vo.setChildren(new ArrayList<>());
            nodeMap.put(group.getId(), vo);
        }

        // 组装树；父节点缺失时按根节点处理，避免脏数据导致整棵树丢失
        List<BitableTableGroupVO> roots = new ArrayList<>();
        for (BitableTableGroup group : groups) {
            BitableTableGroupVO vo = nodeMap.get(group.getId());
            BitableTableGroupVO parent = group.getParentId() == null ? null : nodeMap.get(group.getParentId());
            if (parent == null) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }

        for (BitableTableGroupVO root : roots) {
            accumulateTableCount(root);
        }
        return roots;
    }

    /**
     * 自底向上累加子孙分组的数据表数量
     *
     * @param node 当前节点
     * @return 该节点及其子孙的数据表总数
     */
    private int accumulateTableCount(BitableTableGroupVO node) {
        int total = node.getTableCount() == null ? 0 : node.getTableCount();
        for (BitableTableGroupVO child : node.getChildren()) {
            total += accumulateTableCount(child);
        }
        node.setTotalTableCount(total);
        return total;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createGroup(Long baseId, BitableTableGroupCreateDTO dto, Long userId) {
        Long parentId = dto.getParentId();
        if (parentId != null) {
            BitableTableGroup parent = requireGroup(parentId);
            if (!Objects.equals(parent.getBaseId(), baseId)) {
                throw new BusinessException("父分组不属于当前多维表格");
            }
        }

        BitableTableGroup group = new BitableTableGroup();
        group.setBaseId(baseId);
        group.setParentId(parentId);
        group.setName(dto.getName().trim());
        group.setSortOrder(groupMapper.selectMaxSortOrder(baseId, parentId) + 1);
        group.setCreatorId(userId);
        groupMapper.insert(group);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("groupId", group.getId());
        detail.put("name", group.getName());
        detail.put("parentId", parentId);
        auditHelper.record(baseId, null, userId, OperationType.ADD_TABLE_GROUP, BitableJsonUtils.toJsonString(detail));

        return group.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameGroup(Long id, BitableTableGroupUpdateDTO dto, Long userId) {
        BitableTableGroup group = requireGroup(id);
        String newName = dto.getName().trim();

        UpdateWrapper<BitableTableGroup> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).set("name", newName);
        groupMapper.update(null, wrapper);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("groupId", id);
        detail.put("before", group.getName());
        detail.put("after", newName);
        auditHelper.record(group.getBaseId(), null, userId, OperationType.UPDATE_TABLE_GROUP,
                BitableJsonUtils.toJsonString(detail));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveGroup(Long id, BitableTableGroupMoveDTO dto, Long userId) {
        BitableTableGroup group = requireGroup(id);
        Long targetParentId = dto.getParentId();

        if (Objects.equals(targetParentId, id)) {
            throw new BusinessException("不能把分组移动到它自己下面");
        }
        if (targetParentId != null) {
            BitableTableGroup target = requireGroup(targetParentId);
            if (!Objects.equals(target.getBaseId(), group.getBaseId())) {
                throw new BusinessException("目标分组不属于同一个多维表格");
            }
            // 防环：目标不能是自己或自己的子孙
            if (collectDescendantIds(group.getBaseId(), id).contains(targetParentId)) {
                throw new BusinessException("不能把分组移动到它自己的子分组下面");
            }
        }

        int sortOrder = dto.getSortOrder() != null
                ? dto.getSortOrder()
                : groupMapper.selectMaxSortOrder(group.getBaseId(), targetParentId) + 1;

        UpdateWrapper<BitableTableGroup> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);
        if (targetParentId == null) {
            wrapper.setSql("parent_id = NULL");
        } else {
            wrapper.set("parent_id", targetParentId);
        }
        wrapper.set("sort_order", sortOrder);
        groupMapper.update(null, wrapper);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("groupId", id);
        detail.put("beforeParentId", group.getParentId());
        detail.put("afterParentId", targetParentId);
        detail.put("sortOrder", sortOrder);
        auditHelper.record(group.getBaseId(), null, userId, OperationType.MOVE_TABLE_GROUP,
                BitableJsonUtils.toJsonString(detail));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id, Long userId) {
        BitableTableGroup group = requireGroup(id);
        Long newParentId = group.getParentId();

        // 子分组与数据表上移到父级，不级联删除
        reparentChildren(group.getBaseId(), id, newParentId);
        reassignTables(group.getBaseId(), id, newParentId);

        groupMapper.deleteById(id);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("groupId", id);
        detail.put("name", group.getName());
        detail.put("movedToParentId", newParentId);
        auditHelper.record(group.getBaseId(), null, userId, OperationType.DELETE_TABLE_GROUP,
                BitableJsonUtils.toJsonString(detail));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveTableToGroup(Long tableId, Long groupId, Long userId) {
        BitableTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException("数据表不存在");
        }
        if (groupId != null) {
            BitableTableGroup group = requireGroup(groupId);
            if (!Objects.equals(group.getBaseId(), table.getBaseId())) {
                throw new BusinessException("目标分组不属于该数据表所在的多维表格");
            }
        }

        UpdateWrapper<BitableTable> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", tableId);
        if (groupId == null) {
            wrapper.setSql("group_id = NULL");
        } else {
            wrapper.set("group_id", groupId);
        }
        tableMapper.update(null, wrapper);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tableId", tableId);
        detail.put("beforeGroupId", table.getGroupId());
        detail.put("afterGroupId", groupId);
        auditHelper.record(table.getBaseId(), tableId, userId, OperationType.MOVE_TABLE_TO_GROUP,
                BitableJsonUtils.toJsonString(detail));
    }

    @Override
    public Long getBaseIdByGroupId(Long groupId) {
        BitableTableGroup group = groupMapper.selectById(groupId);
        return group == null ? null : group.getBaseId();
    }

    /**
     * 取分组，不存在则抛业务异常
     */
    private BitableTableGroup requireGroup(Long groupId) {
        BitableTableGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException("分组不存在");
        }
        return group;
    }

    /**
     * 收集某分组的全部子孙分组ID（不含自身）
     */
    private Set<Long> collectDescendantIds(Long baseId, Long groupId) {
        List<BitableTableGroup> all = groupMapper.selectByBaseId(baseId);
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (BitableTableGroup group : all) {
            if (group.getParentId() != null) {
                childrenMap.computeIfAbsent(group.getParentId(), k -> new ArrayList<>()).add(group.getId());
            }
        }

        Set<Long> result = new HashSet<>();
        List<Long> queue = new ArrayList<>();
        queue.add(groupId);
        while (!queue.isEmpty()) {
            Long current = queue.remove(0);
            List<Long> children = childrenMap.get(current);
            if (children == null) {
                continue;
            }
            for (Long child : children) {
                if (result.add(child)) {
                    queue.add(child);
                }
            }
        }
        return result;
    }

    /**
     * 把某分组的直接子分组挂到新的父级下
     */
    private void reparentChildren(Long baseId, Long fromParentId, Long toParentId) {
        UpdateWrapper<BitableTableGroup> wrapper = new UpdateWrapper<>();
        wrapper.eq("base_id", baseId).eq("parent_id", fromParentId);
        if (toParentId == null) {
            wrapper.setSql("parent_id = NULL");
        } else {
            wrapper.set("parent_id", toParentId);
        }
        groupMapper.update(null, wrapper);
    }

    /**
     * 把某分组下直接挂载的数据表改挂到新的分组
     */
    private void reassignTables(Long baseId, Long fromGroupId, Long toGroupId) {
        UpdateWrapper<BitableTable> wrapper = new UpdateWrapper<>();
        wrapper.eq("base_id", baseId).eq("group_id", fromGroupId);
        if (toGroupId == null) {
            wrapper.setSql("group_id = NULL");
        } else {
            wrapper.set("group_id", toGroupId);
        }
        tableMapper.update(null, wrapper);
    }
}
