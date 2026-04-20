package com.demand.system.module.requirement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.requirement.dto.RequirementCreateDTO;
import com.demand.system.module.requirement.dto.RequirementQueryDTO;
import com.demand.system.module.requirement.dto.RequirementUpdateDTO;
import com.demand.system.module.requirement.dto.RequirementVO;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.entity.RequirementHistory;
import com.demand.system.module.requirement.mapper.CustomFieldValueMapper;
import com.demand.system.module.requirement.mapper.RequirementHistoryMapper;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.requirement.service.RequirementService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequirementServiceImpl implements RequirementService {

    private final RequirementMapper requirementMapper;
    private final RequirementHistoryMapper historyMapper;
    private final CustomFieldValueMapper customFieldValueMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    @Override
    public PageResult<RequirementVO> list(RequirementQueryDTO query) {
        Page<Requirement> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<>();

        if (query.getProjectId() != null) {
            wrapper.eq(Requirement::getProjectId, query.getProjectId());
        }
        if (query.getParentId() != null) {
            wrapper.eq(Requirement::getParentId, query.getParentId());
        }
        if (StringUtils.hasText(query.getType())) {
            wrapper.eq(Requirement::getType, query.getType());
        }
        if (StringUtils.hasText(query.getPriority())) {
            wrapper.eq(Requirement::getPriority, query.getPriority());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Requirement::getStatus, query.getStatus());
        }
        if (query.getAssigneeId() != null) {
            wrapper.eq(Requirement::getAssigneeId, query.getAssigneeId());
        }
        if (query.getIterationId() != null) {
            wrapper.eq(Requirement::getIterationId, query.getIterationId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(Requirement::getTitle, query.getKeyword())
                    .or().like(Requirement::getDescription, query.getKeyword()));
        }

        if (StringUtils.hasText(query.getSortField()) && StringUtils.hasText(query.getSortOrder())) {
            if ("asc".equalsIgnoreCase(query.getSortOrder())) {
                wrapper.orderByAsc(getColumnFunction(query.getSortField()));
            } else {
                wrapper.orderByDesc(getColumnFunction(query.getSortField()));
            }
        } else {
            wrapper.orderByDesc(Requirement::getCreatedAt);
        }

        Page<Requirement> resultPage = requirementMapper.selectPage(page, wrapper);

        List<RequirementVO> voList = new ArrayList<>();
        for (Requirement r : resultPage.getRecords()) {
            RequirementVO vo = new RequirementVO();
            BeanUtils.copyProperties(r, vo);
            fillUserNames(vo, r);
            voList.add(vo);
        }

        return new PageResult<>(voList, resultPage.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public RequirementVO getDetail(Long id) {
        Requirement r = requirementMapper.selectById(id);
        if (r == null) {
            throw new BusinessException("需求不存在");
        }
        RequirementVO vo = new RequirementVO();
        BeanUtils.copyProperties(r, vo);
        fillUserNames(vo, r);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(RequirementCreateDTO dto, Long creatorId) {
        Requirement requirement = new Requirement();
        BeanUtils.copyProperties(dto, requirement);
        requirement.setCreatorId(creatorId);
        requirement.setStatus("新建");
        if (requirement.getOrderNum() == null) {
            requirement.setOrderNum(0);
        }
        requirement.setVersion(0);

        requirementMapper.insert(requirement);

        // PRD: 父需求优先级默认传递至子需求
        // 如果有父需求，子需求继承父需求的优先级
        if (dto.getParentId() != null) {
            Requirement parent = requirementMapper.selectById(dto.getParentId());
            if (parent != null && dto.getPriority() == null && parent.getPriority() != null) {
                UpdateWrapper<Requirement> priorityWrapper = new UpdateWrapper<>();
                priorityWrapper.eq("id", requirement.getId()).set("priority", parent.getPriority());
                requirementMapper.update(null, priorityWrapper);
                recordHistory(requirement.getId(), creatorId, "priority", null, "继承父需求优先级: " + parent.getPriority());
            }
        }

        recordHistory(requirement.getId(), creatorId, "create", null, "需求创建");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RequirementUpdateDTO dto, Long userId) {
        Requirement existing = requirementMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException("需求不存在");
        }

        Long operatorId = userId;
        UpdateWrapper<Requirement> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", dto.getId());

        if (dto.getTitle() != null && !Objects.equals(existing.getTitle(), dto.getTitle())) {
            recordHistory(dto.getId(), operatorId, "title", existing.getTitle(), dto.getTitle());
            updateWrapper.set("title", dto.getTitle());
        }
        if (dto.getDescription() != null && !Objects.equals(existing.getDescription(), dto.getDescription())) {
            recordHistory(dto.getId(), operatorId, "description", existing.getDescription(), dto.getDescription());
            updateWrapper.set("description", dto.getDescription());
        }
        if (dto.getType() != null && !Objects.equals(existing.getType(), dto.getType())) {
            recordHistory(dto.getId(), operatorId, "type", existing.getType(), dto.getType());
            updateWrapper.set("type", dto.getType());
        }
        if (dto.getPriority() != null && !Objects.equals(existing.getPriority(), dto.getPriority())) {
            recordHistory(dto.getId(), operatorId, "priority", existing.getPriority(), dto.getPriority());
            updateWrapper.set("priority", dto.getPriority());
        }
        if (dto.getAssigneeId() != null && !Objects.equals(existing.getAssigneeId(), dto.getAssigneeId())) {
            recordHistory(dto.getId(), operatorId, "assigneeId",
                    strVal(existing.getAssigneeId()), strVal(dto.getAssigneeId()));
            updateWrapper.set("assignee_id", dto.getAssigneeId());
        }
        if (dto.getIterationId() != null && !Objects.equals(existing.getIterationId(), dto.getIterationId())) {
            recordHistory(dto.getId(), operatorId, "iterationId",
                    strVal(existing.getIterationId()), strVal(dto.getIterationId()));
            updateWrapper.set("iteration_id", dto.getIterationId());
        }
        if (dto.getModuleId() != null && !Objects.equals(existing.getModuleId(), dto.getModuleId())) {
            recordHistory(dto.getId(), operatorId, "moduleId",
                    strVal(existing.getModuleId()), strVal(dto.getModuleId()));
            updateWrapper.set("module_id", dto.getModuleId());
        }
        if (dto.getDueDate() != null && !Objects.equals(existing.getDueDate(), dto.getDueDate())) {
            recordHistory(dto.getId(), operatorId, "dueDate",
                    strDate(existing.getDueDate()), strDate(dto.getDueDate()));
            updateWrapper.set("due_date", dto.getDueDate());
        }
        if (dto.getEstimatedHours() != null && !Objects.equals(existing.getEstimatedHours(), dto.getEstimatedHours())) {
            recordHistory(dto.getId(), operatorId, "estimatedHours",
                    strDecimal(existing.getEstimatedHours()), strDecimal(dto.getEstimatedHours()));
            updateWrapper.set("estimated_hours", dto.getEstimatedHours());
        }
        if (dto.getActualHours() != null && !Objects.equals(existing.getActualHours(), dto.getActualHours())) {
            recordHistory(dto.getId(), operatorId, "actualHours",
                    strDecimal(existing.getActualHours()), strDecimal(dto.getActualHours()));
            updateWrapper.set("actual_hours", dto.getActualHours());
        }
        if (dto.getStatus() != null && !Objects.equals(existing.getStatus(), dto.getStatus())) {
            recordHistory(dto.getId(), operatorId, "status", existing.getStatus(), dto.getStatus());
            updateWrapper.set("status", dto.getStatus());

            // PRD: 子需求状态汇总后自动更新父需求状态
            // 当子需求状态变更时，更新父需求的汇总状态
            updateParentStatus(existing.getParentId(), dto.getStatus());

            // Send notifications for status change
            sendStatusChangeNotifications(existing, dto.getStatus(), userId);
        }
        if (dto.getOrderNum() != null && !Objects.equals(existing.getOrderNum(), dto.getOrderNum())) {
            recordHistory(dto.getId(), operatorId, "orderNum",
                    strVal(existing.getOrderNum()), strVal(dto.getOrderNum()));
            updateWrapper.set("order_num", dto.getOrderNum());
        }

        requirementMapper.update(null, updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        Requirement requirement = requirementMapper.selectById(id);
        if (requirement == null) {
            throw new BusinessException("需求不存在");
        }

        // 检查权限：只有创建者或admin角色可以删除
        boolean isCreator = requirement.getCreatorId() != null
                && requirement.getCreatorId().equals(userId);
        boolean isAdmin = SecurityUtils.getCurrentUserRoles().contains("admin");

        if (!isCreator && !isAdmin) {
            throw new BusinessException("只有创建者或管理员可以删除需求");
        }

        // 检查是否已流转：只有未流转的需求才能删除
        // 流转判断：status不为"新建"表示已流转
        if (!"新建".equals(requirement.getStatus())) {
            throw new BusinessException("已流转的需求不能删除");
        }

        requirementMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id, Long userId) {
        Requirement existing = requirementMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("需求不存在");
        }

        // 检查删除状态
        if (existing.getDeletedAt() == null || existing.getDeletedAt() == 0) {
            throw new BusinessException("该需求未被删除，无需恢复");
        }

        // 检查权限：只有创建者或admin角色可以恢复
        boolean isCreator = existing.getCreatorId() != null
                && existing.getCreatorId().equals(userId);
        boolean isAdmin = SecurityUtils.getCurrentUserRoles().contains("admin");

        if (!isCreator && !isAdmin) {
            throw new BusinessException("只有创建者或管理员可以恢复需求");
        }

        UpdateWrapper<Requirement> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).set("deleted_at", null);
        requirementMapper.update(null, wrapper);
    }

    @Override
    public List<Map<String, Object>> getHistory(Long requirementId) {
        return historyMapper.selectHistoryByRequirement(requirementId);
    }

    @Override
    public List<Map<String, Object>> getChildren(Long parentId) {
        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Requirement::getParentId, parentId)
                .eq(Requirement::getDeletedAt, 0)
                .orderByAsc(Requirement::getOrderNum);

        List<Requirement> children = requirementMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Requirement r : children) {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", r.getId());
            map.put("title", r.getTitle());
            map.put("status", r.getStatus());
            map.put("priority", r.getPriority());
            map.put("type", r.getType());
            result.add(map);
        }
        return result;
    }

    private void recordHistory(Long requirementId, Long operatorId, String fieldName, String oldValue, String newValue) {
        RequirementHistory history = new RequirementHistory();
        history.setRequirementId(requirementId);
        history.setOperatorId(operatorId);
        history.setFieldName(fieldName);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        historyMapper.insert(history);
    }

    private String strVal(Object val) {
        return val != null ? val.toString() : null;
    }

    private String strDate(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    private String strDecimal(BigDecimal decimal) {
        return decimal != null ? decimal.toString() : null;
    }

    private SFunction<Requirement, ?> getColumnFunction(String sortField) {
        return switch (sortField) {
            case "title" -> Requirement::getTitle;
            case "priority" -> Requirement::getPriority;
            case "status" -> Requirement::getStatus;
            case "dueDate" -> Requirement::getDueDate;
            case "orderNum" -> Requirement::getOrderNum;
            case "createdAt" -> Requirement::getCreatedAt;
            default -> Requirement::getCreatedAt;
        };
    }

    /**
     * PRD: 子需求状态汇总后自动更新父需求状态
     * 汇总规则：
     * - 如果所有子需求都是"已验收"，父需求状态变为"已验收"
     * - 如果所有子需求都是"已上线"，父需求状态变为"已上线"
     * - 如果有子需求处于"开发中"或"测试中"，父需求状态变为"开发中"
     * - 否则父需求保持"评审中"或原有状态
     */
    private void updateParentStatus(Long parentId, String childStatus) {
        if (parentId == null) {
            return;
        }

        Requirement parent = requirementMapper.selectById(parentId);
        if (parent == null || parent.getDeletedAt() != null && parent.getDeletedAt() != 0) {
            return;
        }

        // 查询所有子需求
        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Requirement::getParentId, parentId)
               .eq(Requirement::getDeletedAt, 0);
        List<Requirement> children = requirementMapper.selectList(wrapper);

        if (children.isEmpty()) {
            return;
        }

        // 统计各状态数量
        Set<String> childStatuses = children.stream()
                .map(Requirement::getStatus)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        String newParentStatus = null;

        // 汇总逻辑
        if (childStatuses.size() == 1) {
            // 所有子需求状态一致
            String onlyStatus = childStatuses.iterator().next();
            if ("已验收".equals(onlyStatus)) {
                newParentStatus = "已验收";
            } else if ("已上线".equals(onlyStatus)) {
                newParentStatus = "已上线";
            }
        } else if (!childStatuses.isEmpty()) {
            // 有多个不同状态
            if (childStatuses.contains("开发中") || childStatuses.contains("测试中")) {
                newParentStatus = "开发中";
            } else if (childStatuses.contains("已通过")) {
                newParentStatus = "已通过";
            }
        }

        // 更新父需求状态
        if (newParentStatus != null && !newParentStatus.equals(parent.getStatus())) {
            UpdateWrapper<Requirement> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", parentId).set("status", newParentStatus);
            requirementMapper.update(null, updateWrapper);
        }
    }

    private void fillUserNames(RequirementVO vo, Requirement r) {
        if (r.getCreatorId() != null) {
            User creator = userMapper.selectById(r.getCreatorId());
            if (creator != null) {
                vo.setCreatorName(creator.getRealName());
            }
        }
        if (r.getAssigneeId() != null) {
            User assignee = userMapper.selectById(r.getAssigneeId());
            if (assignee != null) {
                vo.setAssigneeName(assignee.getRealName());
            }
        }
    }

    private void sendStatusChangeNotifications(Requirement requirement, String newStatus, Long operatorId) {
        String title = "需求状态变更";
        String content = String.format("需求【%s】状态已变更为【%s】", requirement.getTitle(), newStatus);

        // Notify creator (if not the operator)
        if (requirement.getCreatorId() != null && !requirement.getCreatorId().equals(operatorId)) {
            notificationService.sendNotification(requirement.getCreatorId(), title, content, "requirement", requirement.getId());
        }
        // Notify assignee (if not the operator and not the creator)
        if (requirement.getAssigneeId() != null
                && !requirement.getAssigneeId().equals(operatorId)
                && !requirement.getAssigneeId().equals(requirement.getCreatorId())) {
            notificationService.sendNotification(requirement.getAssigneeId(), title, content, "requirement", requirement.getId());
        }
    }
}
