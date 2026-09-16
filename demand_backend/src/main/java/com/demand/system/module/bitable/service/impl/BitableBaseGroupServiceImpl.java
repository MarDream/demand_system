package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableBaseGroupCreateDTO;
import com.demand.system.module.bitable.dto.BitableBaseGroupMoveDTO;
import com.demand.system.module.bitable.dto.BitableBaseGroupUpdateDTO;
import com.demand.system.module.bitable.dto.BitableBaseGroupVO;
import com.demand.system.module.bitable.entity.BitableBase;
import com.demand.system.module.bitable.entity.BitableBaseGroup;
import com.demand.system.module.bitable.mapper.BitableBaseGroupMapper;
import com.demand.system.module.bitable.mapper.BitableBaseMapper;
import com.demand.system.module.bitable.service.BitableBaseGroupService;
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
 * 多维表格-Base分组 Service 实现
 */
@Service
public class BitableBaseGroupServiceImpl implements BitableBaseGroupService {

    private final BitableBaseGroupMapper groupMapper;
    private final BitableBaseMapper baseMapper;
    private final BitableConverter converter;

    public BitableBaseGroupServiceImpl(BitableBaseGroupMapper groupMapper,
                                       BitableBaseMapper baseMapper,
                                       BitableConverter converter) {
        this.groupMapper = groupMapper;
        this.baseMapper = baseMapper;
        this.converter = converter;
    }

    @Override
    public List<BitableBaseGroupVO> listGroupTree() {
        List<BitableBaseGroup> groups = groupMapper.selectAll();
        if (groups.isEmpty()) {
            return new ArrayList<>();
        }
        List<BitableBase> bases = baseMapper.selectList(null);

        // 每个分组「直接挂载」的 Base 数量
        Map<Long, Integer> directCount = new HashMap<>();
        for (BitableBase base : bases) {
            if (base.getGroupId() != null) {
                directCount.merge(base.getGroupId(), 1, Integer::sum);
            }
        }

        Map<Long, BitableBaseGroupVO> nodeMap = new LinkedHashMap<>();
        for (BitableBaseGroup group : groups) {
            BitableBaseGroupVO vo = converter.toBaseGroupVO(group);
            int count = directCount.getOrDefault(group.getId(), 0);
            vo.setBaseCount(count);
            vo.setTotalBaseCount(count);
            vo.setChildren(new ArrayList<>());
            nodeMap.put(group.getId(), vo);
        }

        // 组装树；父节点缺失时按根节点处理，避免脏数据导致整棵树丢失
        List<BitableBaseGroupVO> roots = new ArrayList<>();
        for (BitableBaseGroup group : groups) {
            BitableBaseGroupVO vo = nodeMap.get(group.getId());
            BitableBaseGroupVO parent = group.getParentId() == null ? null : nodeMap.get(group.getParentId());
            if (parent == null) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }

        for (BitableBaseGroupVO root : roots) {
            accumulateBaseCount(root);
        }
        return roots;
    }

    /**
     * 自底向上累加子孙分组的 Base 数量
     *
     * @param node 当前节点
     * @return 该节点及其子孙的 Base 总数
     */
    private int accumulateBaseCount(BitableBaseGroupVO node) {
        int total = node.getBaseCount() == null ? 0 : node.getBaseCount();
        for (BitableBaseGroupVO child : node.getChildren()) {
            total += accumulateBaseCount(child);
        }
        node.setTotalBaseCount(total);
        return total;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createGroup(BitableBaseGroupCreateDTO dto, Long userId) {
        Long parentId = dto.getParentId();
        if (parentId != null) {
            requireGroup(parentId);
        }

        BitableBaseGroup group = new BitableBaseGroup();
        group.setParentId(parentId);
        group.setName(dto.getName().trim());
        group.setSortOrder(groupMapper.selectMaxSortOrder(parentId) + 1);
        group.setCreatorId(userId);
        groupMapper.insert(group);
        return group.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameGroup(Long id, BitableBaseGroupUpdateDTO dto, Long userId) {
        BitableBaseGroup group = requireGroup(id);
        String newName = dto.getName().trim();

        UpdateWrapper<BitableBaseGroup> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).set("name", newName);
        groupMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveGroup(Long id, BitableBaseGroupMoveDTO dto, Long userId) {
        BitableBaseGroup group = requireGroup(id);
        Long targetParentId = dto.getParentId();

        if (Objects.equals(targetParentId, id)) {
            throw new BusinessException("不能把分组移动到它自己下面");
        }
        if (targetParentId != null) {
            requireGroup(targetParentId);
            // 防环：目标不能是自己或自己的子孙
            if (collectDescendantIds(id).contains(targetParentId)) {
                throw new BusinessException("不能把分组移动到它自己的子分组下面");
            }
        }

        int sortOrder = dto.getSortOrder() != null
                ? dto.getSortOrder()
                : groupMapper.selectMaxSortOrder(targetParentId) + 1;

        UpdateWrapper<BitableBaseGroup> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);
        if (targetParentId == null) {
            wrapper.setSql("parent_id = NULL");
        } else {
            wrapper.set("parent_id", targetParentId);
        }
        wrapper.set("sort_order", sortOrder);
        groupMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id, Long userId) {
        BitableBaseGroup group = requireGroup(id);
        Long newParentId = group.getParentId();

        // 子分组与 Base 上移到父级，不级联删除
        reparentChildren(id, newParentId);
        reassignBases(id, newParentId);

        groupMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveBaseToGroup(Long baseId, Long groupId, Long userId) {
        BitableBase base = baseMapper.selectById(baseId);
        if (base == null) {
            throw new BusinessException("多维表格不存在");
        }
        if (groupId != null) {
            requireGroup(groupId);
        }

        UpdateWrapper<BitableBase> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", baseId);
        if (groupId == null) {
            wrapper.setSql("group_id = NULL");
        } else {
            wrapper.set("group_id", groupId);
        }
        baseMapper.update(null, wrapper);
    }

    /**
     * 取分组，不存在则抛业务异常
     */
    private BitableBaseGroup requireGroup(Long groupId) {
        BitableBaseGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException("分组不存在");
        }
        return group;
    }

    /**
     * 收集某分组的全部子孙分组ID（不含自身）
     */
    private Set<Long> collectDescendantIds(Long groupId) {
        List<BitableBaseGroup> all = groupMapper.selectAll();
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (BitableBaseGroup group : all) {
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
    private void reparentChildren(Long fromParentId, Long toParentId) {
        UpdateWrapper<BitableBaseGroup> wrapper = new UpdateWrapper<>();
        wrapper.eq("parent_id", fromParentId);
        if (toParentId == null) {
            wrapper.setSql("parent_id = NULL");
        } else {
            wrapper.set("parent_id", toParentId);
        }
        groupMapper.update(null, wrapper);
    }

    /**
     * 把某分组下直接挂载的 Base 改挂到新的分组
     */
    private void reassignBases(Long fromGroupId, Long toGroupId) {
        UpdateWrapper<BitableBase> wrapper = new UpdateWrapper<>();
        wrapper.eq("group_id", fromGroupId);
        if (toGroupId == null) {
            wrapper.setSql("group_id = NULL");
        } else {
            wrapper.set("group_id", toGroupId);
        }
        baseMapper.update(null, wrapper);
    }
}
