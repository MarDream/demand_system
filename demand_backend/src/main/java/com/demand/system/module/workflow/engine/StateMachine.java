package com.demand.system.module.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.module.requirement.dto.RequirementFieldAlias;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.workflow.entity.WorkflowState;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.entity.WorkflowTransitionRecord;
import com.demand.system.module.workflow.mapper.WorkflowStateMapper;
import com.demand.system.module.workflow.mapper.WorkflowTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowTransitionRecordMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Component
public class StateMachine {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StateMachine.class);

    private final RequirementMapper requirementMapper;
    private final WorkflowTransitionMapper transitionMapper;
    private final WorkflowStateMapper stateMapper;
    private final WorkflowTransitionRecordMapper transitionRecordMapper;
    private final PermissionEngine permissionEngine;
    private final WorkflowDefinitionEngine workflowDefinitionEngine;

    public StateMachine(RequirementMapper requirementMapper, WorkflowTransitionMapper transitionMapper,
                       WorkflowStateMapper stateMapper, WorkflowTransitionRecordMapper transitionRecordMapper,
                       PermissionEngine permissionEngine, WorkflowDefinitionEngine workflowDefinitionEngine) {
        this.requirementMapper = requirementMapper;
        this.transitionMapper = transitionMapper;
        this.stateMapper = stateMapper;
        this.transitionRecordMapper = transitionRecordMapper;
        this.permissionEngine = permissionEngine;
        this.workflowDefinitionEngine = workflowDefinitionEngine;
    }

    /**
     * 执行状态转换
     *
     * @param requirementId 需求ID
     * @param fromStateId   起始状态ID
     * @param toStateId     目标状态ID
     * @param userId        操作用户ID
     * @param comment       转换备注
     * @return true 如果转换成功
     */
    public boolean transition(Long requirementId, Long fromStateId, Long toStateId, Long userId, String comment) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            log.warn("Requirement {} not found", requirementId);
            return false;
        }

        WorkflowState fromState = stateMapper.selectById(fromStateId);
        WorkflowState targetState = stateMapper.selectById(toStateId);
        if (fromState == null || targetState == null) {
            log.error("From or target state not found: {} -> {}", fromStateId, toStateId);
            return false;
        }

        boolean definitionDriven = StringUtils.hasText(requirement.getType())
                && workflowDefinitionEngine.hasActiveDefinition(requirement.getType());
        Optional<WorkflowDefinitionEngine.ResolvedTransitionSpec> resolvedSpec = definitionDriven
                ? workflowDefinitionEngine.resolveTransition(requirement, fromState.getName(), targetState.getName())
                : Optional.empty();

        WorkflowTransition transition = transitionMapper.selectOne(new LambdaQueryWrapper<WorkflowTransition>()
                .eq(WorkflowTransition::getFromStateId, fromStateId)
                .eq(WorkflowTransition::getToStateId, toStateId)
                .last("LIMIT 1"));

        if (!definitionDriven && transition == null) {
            log.warn("Transition from state {} to state {} does not exist", fromStateId, toStateId);
            return false;
        }
        if (definitionDriven && resolvedSpec.isEmpty()) {
            log.warn("No executable BPMN path from {} to {}", fromState.getName(), targetState.getName());
            return false;
        }

        if (!permissionEngine.canTransition(requirementId, fromStateId, toStateId, userId)) {
            log.warn("User {} not permitted to transition from {} to {}", userId, fromStateId, toStateId);
            return false;
        }

        String requiredFields = resolvedSpec
                .map(WorkflowDefinitionEngine.ResolvedTransitionSpec::requiredFieldsJson)
                .orElseGet(() -> transition == null ? null : transition.getRequiredFields());
        if (StringUtils.hasText(requiredFields)) {
            if (!validateRequiredFields(requirement, requiredFields)) {
                log.warn("Required fields not filled for requirement {}", requirementId);
                return false;
            }
        }

        String fromStateName = fromState.getName();

        Requirement currentReq = requirementMapper.selectById(requirementId);
        if (currentReq == null) {
            log.warn("Requirement {} disappeared during transition", requirementId);
            return false;
        }
        String currentStateName = currentReq.getStatus();
        if (!currentStateName.equals(fromStateName)) {
            log.warn("Requirement {} status changed concurrently from {} to {}, transition aborted",
                    requirementId, fromStateName, currentStateName);
            return false;
        }

        // 7. 使用乐观锁更新需求状态（状态条件作为乐观锁检查）
        UpdateWrapper<Requirement> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", requirementId)
                     .eq("status", currentStateName)  // 乐观锁条件：状态未变化
                     .set("status", targetState.getName());
        int updated = requirementMapper.update(null, updateWrapper);
        if (updated <= 0) {
            log.warn("Requirement {} status changed concurrently during update, transition aborted", requirementId);
            return false;
        }

        // 8. 记录转换
        WorkflowTransitionRecord record = new WorkflowTransitionRecord();
        record.setRequirementId(requirementId);
        record.setFromStateId(fromStateId);
        record.setToStateId(toStateId);
        record.setOperatorId(userId);
        record.setComment(comment);
        record.setCreatedAt(LocalDateTime.now());
        transitionRecordMapper.insert(record);

        log.info("Requirement {} transitioned from state {} to state {} by user {}",
                requirementId, fromStateId, toStateId, userId);
        return true;
    }

    /**
     * 验证必填字段是否已填写
     */
    private boolean validateRequiredFields(Requirement requirement, String requiredFieldsJson) {
        // 简单解析：requiredFields 是 JSON 数组格式如 ["title","description","priority"]
        String cleaned = requiredFieldsJson.replaceAll("[\"\\[\\]]", "").trim();
        if (cleaned.isEmpty()) {
            return true;
        }

        String[] fields = cleaned.split(",");
        for (String field : fields) {
            field = RequirementFieldAlias.normalize(field);
            if (field.isEmpty()) continue;
            try {
                Object value = getFieldValue(requirement, field);
                if (value == null) {
                    log.warn("Required field '{}' is null", field);
                    return false;
                }
            } catch (NoSuchFieldException e) {
                log.warn("Required field '{}' not found on Requirement entity", field);
            }
        }
        return true;
    }

    private Object getFieldValue(Requirement requirement, String fieldName) throws NoSuchFieldException {
        return switch (fieldName) {
            case "title" -> requirement.getTitle();
            case "description" -> requirement.getDescription();
            case "type" -> requirement.getType();
            case "priority" -> requirement.getPriority();
            case "status" -> requirement.getStatus();
            case "assigneeId" -> requirement.getAssigneeId();
            case "moduleId" -> requirement.getModuleId();
            case "iterationId" -> requirement.getIterationId();
            case "startDate" -> requirement.getStartDate();
            case "estimatedHours" -> requirement.getEstimatedHours();
            case "dueDate" -> requirement.getDueDate();
            case "attachments" -> requirement.getAttachments();
            default -> throw new NoSuchFieldException(fieldName);
        };
    }
}
