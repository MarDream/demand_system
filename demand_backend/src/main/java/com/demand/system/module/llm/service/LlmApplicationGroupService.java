package com.demand.system.module.llm.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.llm.dto.LlmApplicationGroupCreateDTO;
import com.demand.system.module.llm.dto.LlmApplicationGroupMoveDTO;
import com.demand.system.module.llm.dto.LlmApplicationGroupUpdateDTO;
import com.demand.system.module.llm.dto.LlmApplicationGroupVO;
import com.demand.system.module.llm.entity.LlmApplication;
import com.demand.system.module.llm.entity.LlmApplicationGroup;
import com.demand.system.module.llm.mapper.LlmApplicationGroupMapper;
import com.demand.system.module.llm.mapper.LlmApplicationMapper;
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
 * LLM 功能点模型应用分组 Service
 * <p>
 * 分组通过 {@code parentId} 自关联形成任意层级的目录树，用于「模型配置 → 模型应用」页
 * 左侧目录树，与管理多维表格数据表分组的方式保持一致。
 * 树上的「全部应用」「未分组」为前端虚拟节点，不落库。
 */
@Service
public class LlmApplicationGroupService {

    private final LlmApplicationGroupMapper groupMapper;
    private final LlmApplicationMapper applicationMapper;

    public LlmApplicationGroupService(LlmApplicationGroupMapper groupMapper,
                                      LlmApplicationMapper applicationMapper) {
        this.groupMapper = groupMapper;
        this.applicationMapper = applicationMapper;
    }

    /**
     * 查询分组树（含各节点功能点数量）
     *
     * @return 根分组列表，children 递归嵌套
     */
    public List<LlmApplicationGroupVO> listGroupTree() {
        List<LlmApplicationGroup> groups = groupMapper.selectAllGroups();
        if (groups.isEmpty()) {
            return new ArrayList<>();
        }
        List<LlmApplication> applications = applicationMapper.selectList(null);

        // 每个分组「直接挂载」的功能点数量
        Map<Long, Integer> directCount = new HashMap<>();
        for (LlmApplication application : applications) {
            if (application.getGroupId() != null) {
                directCount.merge(application.getGroupId(), 1, Integer::sum);
            }
        }

        Map<Long, LlmApplicationGroupVO> nodeMap = new LinkedHashMap<>();
        for (LlmApplicationGroup group : groups) {
            LlmApplicationGroupVO vo = toVO(group);
            int count = directCount.getOrDefault(group.getId(), 0);
            vo.setApplicationCount(count);
            vo.setTotalApplicationCount(count);
            vo.setChildren(new ArrayList<>());
            nodeMap.put(group.getId(), vo);
        }

        // 组装树；父节点缺失时按根节点处理，避免脏数据导致整棵树丢失
        List<LlmApplicationGroupVO> roots = new ArrayList<>();
        for (LlmApplicationGroup group : groups) {
            LlmApplicationGroupVO vo = nodeMap.get(group.getId());
            LlmApplicationGroupVO parent = group.getParentId() == null ? null : nodeMap.get(group.getParentId());
            if (parent == null) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }

        for (LlmApplicationGroupVO root : roots) {
            accumulateApplicationCount(root);
        }
        return roots;
    }

    /**
     * 自底向上累加子孙分组的功能点数量
     *
     * @param node 当前节点
     * @return 该节点及其子孙的功能点总数
     */
    private int accumulateApplicationCount(LlmApplicationGroupVO node) {
        int total = node.getApplicationCount() == null ? 0 : node.getApplicationCount();
        for (LlmApplicationGroupVO child : node.getChildren()) {
            total += accumulateApplicationCount(child);
        }
        node.setTotalApplicationCount(total);
        return total;
    }

    /**
     * 创建分组
     *
     * @param dto 创建参数（name、parentId）
     * @return 新分组ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createGroup(LlmApplicationGroupCreateDTO dto) {
        Long parentId = dto.getParentId();
        if (parentId != null) {
            requireGroup(parentId);
        }

        LlmApplicationGroup group = new LlmApplicationGroup();
        group.setParentId(parentId);
        group.setName(dto.getName().trim());
        group.setSortOrder(groupMapper.selectMaxSortOrder(parentId) + 1);
        groupMapper.insert(group);
        return group.getId();
    }

    /**
     * 重命名分组
     *
     * @param id  分组ID
     * @param dto 更新参数
     */
    @Transactional(rollbackFor = Exception.class)
    public void renameGroup(Long id, LlmApplicationGroupUpdateDTO dto) {
        requireGroup(id);
        UpdateWrapper<LlmApplicationGroup> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).eq("deleted_at", 0).set("name", dto.getName().trim());
        groupMapper.update(null, wrapper);
    }

    /**
     * 移动分组（变更父级 / 同级排序），服务端做防环校验
     *
     * @param id  分组ID
     * @param dto 移动参数
     */
    @Transactional(rollbackFor = Exception.class)
    public void moveGroup(Long id, LlmApplicationGroupMoveDTO dto) {
        LlmApplicationGroup group = requireGroup(id);
        Long targetParentId = dto == null ? null : dto.getParentId();

        if (Objects.equals(targetParentId, id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能把分组移动到它自己下面");
        }
        if (targetParentId != null) {
            requireGroup(targetParentId);
            // 防环：目标不能是自己或自己的子孙
            if (collectDescendantIds(id).contains(targetParentId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不能把分组移动到它自己的子分组下面");
            }
        }

        Integer sortOrder = dto == null ? null : dto.getSortOrder();
        int finalSortOrder = sortOrder != null ? sortOrder : groupMapper.selectMaxSortOrder(targetParentId) + 1;

        UpdateWrapper<LlmApplicationGroup> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", group.getId()).eq("deleted_at", 0);
        if (targetParentId == null) {
            wrapper.setSql("parent_id = NULL");
        } else {
            wrapper.set("parent_id", targetParentId);
        }
        wrapper.set("sort_order", finalSortOrder);
        groupMapper.update(null, wrapper);
    }

    /**
     * 删除分组：子分组与功能点上移到父级，不级联删除
     *
     * @param id 分组ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id) {
        LlmApplicationGroup group = requireGroup(id);
        Long newParentId = group.getParentId();

        reparentChildren(id, newParentId);
        reassignApplications(id, newParentId);

        groupMapper.deleteById(id);
    }

    /**
     * 取分组，不存在则抛业务异常
     */
    private LlmApplicationGroup requireGroup(Long groupId) {
        LlmApplicationGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分组不存在");
        }
        return group;
    }

    /**
     * 收集某分组的全部子孙分组ID（不含自身）
     */
    private Set<Long> collectDescendantIds(Long groupId) {
        List<LlmApplicationGroup> all = groupMapper.selectAllGroups();
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (LlmApplicationGroup group : all) {
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
        UpdateWrapper<LlmApplicationGroup> wrapper = new UpdateWrapper<>();
        wrapper.eq("parent_id", fromParentId).eq("deleted_at", 0);
        if (toParentId == null) {
            wrapper.setSql("parent_id = NULL");
        } else {
            wrapper.set("parent_id", toParentId);
        }
        groupMapper.update(null, wrapper);
    }

    /**
     * 把某分组下直接挂载的功能点改挂到新的分组
     */
    private void reassignApplications(Long fromGroupId, Long toGroupId) {
        UpdateWrapper<LlmApplication> wrapper = new UpdateWrapper<>();
        wrapper.eq("group_id", fromGroupId);
        if (toGroupId == null) {
            wrapper.setSql("group_id = NULL");
        } else {
            wrapper.set("group_id", toGroupId);
        }
        applicationMapper.update(null, wrapper);
    }

    private LlmApplicationGroupVO toVO(LlmApplicationGroup group) {
        LlmApplicationGroupVO vo = new LlmApplicationGroupVO();
        vo.setId(group.getId());
        vo.setParentId(group.getParentId());
        vo.setName(group.getName());
        vo.setSortOrder(group.getSortOrder());
        vo.setCreatedAt(group.getCreatedAt());
        vo.setUpdatedAt(group.getUpdatedAt());
        return vo;
    }
}
