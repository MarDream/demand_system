package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.workflow.dto.FlowTransitionRequest;
import com.demand.system.module.workflow.dto.TransitionVO;
import com.demand.system.module.workflow.entity.*;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowEngineService {

    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowInstanceTransitionMapper transitionMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final RequirementMapper requirementMapper;
    private final UserMapper userMapper;

    @Transactional
    public void initWorkflow(Long requirementId, Long workflowVersionId) {
        WorkflowNode startNode = nodeMapper.selectOne(
            new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, workflowVersionId)
                .eq(WorkflowNode::getNodeType, "start")
        );
        if (startNode == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流缺少开始节点");
        }

        WorkflowInstance instance = new WorkflowInstance();
        instance.setRequirementId(requirementId);
        instance.setWorkflowVersionId(workflowVersionId);
        instance.setCurrentNodeId(startNode.getNodeId());
        instance.setStatus("running");
        instanceMapper.insert(instance);

        WorkflowInstanceTransition transition = new WorkflowInstanceTransition();
        transition.setInstanceId(instance.getId());
        transition.setRequirementId(requirementId);
        transition.setToNodeId(startNode.getNodeId());
        transition.setToNodeName(startNode.getNodeName());
        transition.setOperatorId(SecurityUtils.getCurrentUserId());
        transition.setAction("submit");
        transition.setStartedAt(LocalDateTime.now());
        transitionMapper.insert(transition);

        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, requirementId)
            .set(Requirement::getWorkflowInstanceId, instance.getId())
            .set(Requirement::getNodeStatus, "DRAFT")
            .set(Requirement::getIsDraft, false)
        );
    }

    @Transactional
    public void transition(FlowTransitionRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        Requirement requirement = requirementMapper.selectById(request.getRequirementId());
        if (requirement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "需求不存在");
        }

        WorkflowInstance instance = instanceMapper.selectOne(
            new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getRequirementId, request.getRequirementId())
        );
        if (instance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流实例不存在");
        }
        if ("completed".equals(instance.getStatus()) || "cancelled".equals(instance.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流已结束，无法流转");
        }

        WorkflowNode currentNode = getNode(instance.getWorkflowVersionId(), instance.getCurrentNodeId());
        WorkflowNode targetNode = getNode(instance.getWorkflowVersionId(), request.getToNodeId());

        validateTransition(instance, currentNode, targetNode, request.getAction(), operatorId);

        closeCurrentTransition(instance.getId());

        WorkflowInstanceTransition newTransition = new WorkflowInstanceTransition();
        newTransition.setInstanceId(instance.getId());
        newTransition.setRequirementId(request.getRequirementId());
        newTransition.setFromNodeId(instance.getCurrentNodeId());
        newTransition.setFromNodeName(currentNode != null ? currentNode.getNodeName() : "");
        newTransition.setToNodeId(request.getToNodeId());
        newTransition.setToNodeName(targetNode.getNodeName());
        newTransition.setOperatorId(operatorId);
        newTransition.setAction(request.getAction() != null ? request.getAction() : "submit");
        newTransition.setComment(request.getComment());
        newTransition.setStartedAt(LocalDateTime.now());
        transitionMapper.insert(newTransition);

        String newStatus = "running";
        if ("cancel".equals(request.getAction())) {
            newStatus = "cancelled";
        } else if (targetNode != null && "end".equals(targetNode.getNodeType())) {
            newStatus = "completed";
        }

        instanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
            .eq(WorkflowInstance::getId, instance.getId())
            .set(WorkflowInstance::getPreviousNodeId, instance.getCurrentNodeId())
            .set(WorkflowInstance::getCurrentNodeId, request.getToNodeId())
            .set(WorkflowInstance::getStatus, newStatus)
        );

        String nodeStatusCode = getNodeStatusCode(targetNode);
        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, request.getRequirementId())
            .set(Requirement::getNodeStatus, nodeStatusCode)
        );
    }

    @Transactional
    public void rollback(Long requirementId, String comment) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        WorkflowInstance instance = getRunningInstance(requirementId);

        if (instance.getPreviousNodeId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已在开始节点，无法回退");
        }

        WorkflowNode currentNode = getNode(instance.getWorkflowVersionId(), instance.getCurrentNodeId());
        if ("end".equals(currentNode.getNodeType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束节点不可回退");
        }

        WorkflowNode previousNode = getNode(instance.getWorkflowVersionId(), instance.getPreviousNodeId());

        closeCurrentTransition(instance.getId());

        WorkflowInstanceTransition rollbackTransition = new WorkflowInstanceTransition();
        rollbackTransition.setInstanceId(instance.getId());
        rollbackTransition.setRequirementId(requirementId);
        rollbackTransition.setFromNodeId(instance.getCurrentNodeId());
        rollbackTransition.setFromNodeName(currentNode.getNodeName());
        rollbackTransition.setToNodeId(instance.getPreviousNodeId());
        rollbackTransition.setToNodeName(previousNode != null ? previousNode.getNodeName() : "");
        rollbackTransition.setOperatorId(operatorId);
        rollbackTransition.setAction("rollback");
        rollbackTransition.setComment(comment);
        rollbackTransition.setStartedAt(LocalDateTime.now());
        transitionMapper.insert(rollbackTransition);

        instanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
            .eq(WorkflowInstance::getId, instance.getId())
            .set(WorkflowInstance::getCurrentNodeId, instance.getPreviousNodeId())
            .set(WorkflowInstance::getPreviousNodeId, instance.getCurrentNodeId())
        );

        String nodeStatusCode = getNodeStatusCode(previousNode);
        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, requirementId)
            .set(Requirement::getNodeStatus, nodeStatusCode)
        );
    }

    @Transactional
    public void cancel(Long requirementId, String comment) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        WorkflowInstance instance = getRunningInstance(requirementId);

        WorkflowNode currentNode = getNode(instance.getWorkflowVersionId(), instance.getCurrentNodeId());
        if ("end".equals(currentNode.getNodeType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束节点不可取消");
        }

        closeCurrentTransition(instance.getId());

        WorkflowInstanceTransition cancelTransition = new WorkflowInstanceTransition();
        cancelTransition.setInstanceId(instance.getId());
        cancelTransition.setRequirementId(requirementId);
        cancelTransition.setFromNodeId(instance.getCurrentNodeId());
        cancelTransition.setFromNodeName(currentNode.getNodeName());
        cancelTransition.setToNodeId("cancelled");
        cancelTransition.setToNodeName("已取消");
        cancelTransition.setOperatorId(operatorId);
        cancelTransition.setAction("cancel");
        cancelTransition.setComment(comment);
        cancelTransition.setStartedAt(LocalDateTime.now());
        transitionMapper.insert(cancelTransition);

        instanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
            .eq(WorkflowInstance::getId, instance.getId())
            .set(WorkflowInstance::getStatus, "cancelled")
        );

        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, requirementId)
            .set(Requirement::getNodeStatus, "CANCELLED")
        );
    }

    public void saveDraft(Long requirementId) {
        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, requirementId)
            .set(Requirement::getIsDraft, true)
        );
    }

    public List<TransitionVO> getTransitionHistory(Long requirementId) {
        List<WorkflowInstanceTransition> transitions = transitionMapper.selectList(
            new LambdaQueryWrapper<WorkflowInstanceTransition>()
                .eq(WorkflowInstanceTransition::getRequirementId, requirementId)
                .orderByAsc(WorkflowInstanceTransition::getCreatedAt)
        );

        return transitions.stream().map(t -> {
            TransitionVO vo = new TransitionVO();
            vo.setId(t.getId());
            vo.setInstanceId(t.getInstanceId());
            vo.setRequirementId(t.getRequirementId());
            vo.setFromNodeId(t.getFromNodeId());
            vo.setFromNodeName(t.getFromNodeName());
            vo.setToNodeId(t.getToNodeId());
            vo.setToNodeName(t.getToNodeName());
            vo.setOperatorId(t.getOperatorId());
            vo.setAction(t.getAction());
            vo.setComment(t.getComment());
            vo.setStartedAt(t.getStartedAt());
            vo.setCompletedAt(t.getCompletedAt());
            vo.setDurationSeconds(t.getDurationSeconds());
            vo.setDurationDisplay(formatDuration(t.getDurationSeconds()));
            vo.setCreatedAt(t.getCreatedAt());

            if (t.getOperatorId() != null) {
                User user = userMapper.selectById(t.getOperatorId());
                if (user != null) {
                    vo.setOperatorName(user.getRealName());
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private void validateTransition(WorkflowInstance instance, WorkflowNode currentNode,
                                     WorkflowNode targetNode, String action, Long operatorId) {
        if ("cancel".equals(action)) {
            if ("end".equals(currentNode.getNodeType())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "结束节点不可取消");
            }
            return;
        }

        boolean edgeExists = edgeMapper.selectCount(
            new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, instance.getWorkflowVersionId())
                .eq(WorkflowEdge::getSourceNodeId, instance.getCurrentNodeId())
                .eq(WorkflowEdge::getTargetNodeId, targetNode.getNodeId())
        ) > 0;

        if (!edgeExists && !"rollback".equals(action)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许从当前节点流转到目标节点");
        }

        if (currentNode.getAssigneeType() != null) {
            validatePermission(currentNode, operatorId);
        }
    }

    private void validatePermission(WorkflowNode node, Long operatorId) {
        String assigneeType = node.getAssigneeType();
        if ("SPECIFIED_USER".equals(assigneeType)) {
            if (node.getAssigneeUserIds() == null || !node.getAssigneeUserIds().contains(operatorId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
            }
        } else if ("SPECIFIED_ROLE".equals(assigneeType)) {
            // 角色校验由 Spring Security 处理，这里做简化检查
        }
        // 争抢式：绑定角色时所有有该角色的人可见可操作，谁先流转归谁
    }

    private void closeCurrentTransition(Long instanceId) {
        WorkflowInstanceTransition lastTransition = transitionMapper.selectOne(
            new LambdaQueryWrapper<WorkflowInstanceTransition>()
                .eq(WorkflowInstanceTransition::getInstanceId, instanceId)
                .isNull(WorkflowInstanceTransition::getCompletedAt)
                .orderByDesc(WorkflowInstanceTransition::getId)
                .last("LIMIT 1")
        );
        if (lastTransition != null) {
            LocalDateTime now = LocalDateTime.now();
            long seconds = Duration.between(lastTransition.getStartedAt(), now).getSeconds();
            transitionMapper.update(null, new LambdaUpdateWrapper<WorkflowInstanceTransition>()
                .eq(WorkflowInstanceTransition::getId, lastTransition.getId())
                .set(WorkflowInstanceTransition::getCompletedAt, now)
                .set(WorkflowInstanceTransition::getDurationSeconds, seconds)
            );
        }
    }

    private WorkflowNode getNode(Long versionId, String nodeId) {
        return nodeMapper.selectOne(
            new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, versionId)
                .eq(WorkflowNode::getNodeId, nodeId)
        );
    }

    private WorkflowInstance getRunningInstance(Long requirementId) {
        WorkflowInstance instance = instanceMapper.selectOne(
            new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getRequirementId, requirementId)
        );
        if (instance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流实例不存在");
        }
        if (!"running".equals(instance.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流已结束");
        }
        return instance;
    }

    private String getNodeStatusCode(WorkflowNode node) {
        if (node == null) return "DRAFT";
        if (node.getProperties() != null && node.getProperties().get("nodeStatusCode") != null) {
            return node.getProperties().get("nodeStatusCode").toString();
        }
        return "DRAFT";
    }

    private String formatDuration(Long seconds) {
        if (seconds == null) return "-";
        if (seconds < 60) return seconds + "秒";
        if (seconds < 3600) return (seconds / 60) + "分" + (seconds % 60) + "秒";
        if (seconds < 86400) return (seconds / 3600) + "时" + ((seconds % 3600) / 60) + "分";
        if (seconds < 2592000L) return (seconds / 86400) + "天" + ((seconds % 86400) / 3600) + "时";
        if (seconds < 31536000L) return (seconds / 2592000L) + "月" + ((seconds % 2592000L) / 86400) + "天";
        return (seconds / 31536000L) + "年" + ((seconds % 31536000L) / 2592000L) + "月";
    }
}
