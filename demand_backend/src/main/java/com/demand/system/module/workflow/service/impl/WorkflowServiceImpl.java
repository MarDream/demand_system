package com.demand.system.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.workflow.dto.NodeConfigDTO;
import com.demand.system.module.workflow.dto.TransitionResponse;
import com.demand.system.module.workflow.dto.WorkflowDefinitionDTO;
import com.demand.system.module.workflow.engine.PermissionEngine;
import com.demand.system.module.workflow.engine.StateMachine;
import com.demand.system.module.workflow.entity.WorkflowState;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowStateMapper;
import com.demand.system.module.workflow.mapper.WorkflowTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowStateMapper stateMapper;
    private final WorkflowTransitionMapper transitionMapper;
    private final WorkflowVersionMapper versionMapper;
    private final StateMachine stateMachine;
    private final PermissionEngine permissionEngine;
    private final RequirementMapper requirementMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<WorkflowState> getStates(Long projectId) {
        LambdaQueryWrapper<WorkflowState> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowState::getProjectId, projectId)
               .orderByAsc(WorkflowState::getSortOrder);
        return stateMapper.selectList(wrapper);
    }

    @Override
    public List<WorkflowTransition> getTransitions(Long projectId) {
        LambdaQueryWrapper<WorkflowTransition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowTransition::getProjectId, projectId);
        return transitionMapper.selectList(wrapper);
    }

    @Override
    public List<WorkflowTransition> getAvailableTransitions(Long requirementId, Long userId) {
        // 获取需求当前状态
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null || requirement.getStatus() == null) {
            log.warn("Requirement {} not found or has no status", requirementId);
            return Collections.emptyList();
        }

        // 通过状态名称查找对应的 workflow_state
        LambdaQueryWrapper<WorkflowState> stateWrapper = new LambdaQueryWrapper<>();
        stateWrapper.eq(WorkflowState::getProjectId, requirement.getProjectId())
                    .eq(WorkflowState::getName, requirement.getStatus());
        WorkflowState currentState = stateMapper.selectOne(stateWrapper);

        if (currentState == null) {
            log.warn("No workflow state found for status '{}' in project {}", requirement.getStatus(), requirement.getProjectId());
            return Collections.emptyList();
        }

        // 查询所有从当前状态出发的转换
        LambdaQueryWrapper<WorkflowTransition> transWrapper = new LambdaQueryWrapper<>();
        transWrapper.eq(WorkflowTransition::getFromStateId, currentState.getId())
                    .eq(WorkflowTransition::getProjectId, requirement.getProjectId());
        List<WorkflowTransition> allTransitions = transitionMapper.selectList(transWrapper);

        // 过滤有权限的转换
        return allTransitions.stream()
                .filter(t -> permissionEngine.canTransition(requirementId, currentState.getId(), t.getToStateId(), userId))
                .collect(Collectors.toList());
    }

    @Override
    public TransitionResponse executeTransition(Long requirementId, Long targetStateId, Long userId, String comment) {
        // 获取需求当前状态
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            return TransitionResponse.builder().success(false).build();
        }

        // 通过状态名称查找当前 workflow_state
        LambdaQueryWrapper<WorkflowState> stateWrapper = new LambdaQueryWrapper<>();
        stateWrapper.eq(WorkflowState::getProjectId, requirement.getProjectId())
                    .eq(WorkflowState::getName, requirement.getStatus());
        WorkflowState currentState = stateMapper.selectOne(stateWrapper);

        if (currentState == null) {
            return TransitionResponse.builder().success(false).build();
        }

        // 执行转换
        boolean success = stateMachine.transition(requirementId, currentState.getId(), targetStateId, userId, comment);

        if (!success) {
            return TransitionResponse.builder().success(false).build();
        }

        // 获取目标状态名称
        WorkflowState targetState = stateMapper.selectById(targetStateId);
        String newStatus = targetState != null ? targetState.getName() : null;

        // 获取新的可用转换列表
        List<WorkflowTransition> available = getAvailableTransitions(requirementId, userId);

        return TransitionResponse.builder()
                .success(true)
                .newStatus(newStatus)
                .availableTransitions(available)
                .build();
    }

    @Override
    public List<WorkflowVersion> getVersions(Long projectId) {
        LambdaQueryWrapper<WorkflowVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowVersion::getProjectId, projectId)
               .orderByDesc(WorkflowVersion::getVersion);
        return versionMapper.selectList(wrapper);
    }

    @Override
    public void createVersion(WorkflowVersion version) {
        // 自动递增版本号
        LambdaQueryWrapper<WorkflowVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowVersion::getProjectId, version.getProjectId())
               .orderByDesc(WorkflowVersion::getVersion)
               .last("LIMIT 1");
        WorkflowVersion latest = versionMapper.selectOne(wrapper);

        int nextVersion = 1;
        if (latest != null && latest.getVersion() != null) {
            nextVersion = latest.getVersion() + 1;
        }

        version.setVersion(nextVersion);
        version.setIsActive(0);
        if (version.getCreatedAt() == null) {
            version.setCreatedAt(LocalDateTime.now());
        }
        versionMapper.insert(version);
    }

    @Override
    public void updateVersion(Long id, WorkflowVersion version) {
        WorkflowVersion existing = versionMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Workflow version not found: " + id);
        }

        if (version.getName() != null) {
            existing.setName(version.getName());
        }
        if (version.getDefinition() != null) {
            existing.setDefinition(version.getDefinition());
        }
        versionMapper.updateById(existing);
    }

    @Override
    public void activateVersion(Long id, Long projectId) {
        // 先将该项目下所有版本设为非激活
        UpdateWrapper<WorkflowVersion> deactivateWrapper = new UpdateWrapper<>();
        deactivateWrapper.eq("project_id", projectId).set("is_active", 0);
        versionMapper.update(null, deactivateWrapper);

        // 再将指定版本设为激活
        UpdateWrapper<WorkflowVersion> activateWrapper = new UpdateWrapper<>();
        activateWrapper.eq("id", id).set("is_active", 1);
        versionMapper.update(null, activateWrapper);
    }

    @Override
    public List<String> validateWorkflow(String definition) {
        List<String> errors = new ArrayList<>();
        if (definition == null || definition.trim().isEmpty()) {
            errors.add("工作流定义不能为空");
            return errors;
        }

        try {
            WorkflowDefinitionDTO workflow = objectMapper.readValue(definition, WorkflowDefinitionDTO.class);

            if (workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
                errors.add("工作流必须包含至少一个节点");
                return errors;
            }

            Set<String> nodeIds = workflow.getNodes().stream()
                    .map(NodeConfigDTO::getNodeId)
                    .collect(Collectors.toSet());

            List<com.demand.system.module.workflow.dto.EdgeDTO> edges = workflow.getEdges();
            if (edges == null) {
                edges = Collections.emptyList();
            }

            // 检查孤立节点（没有入边的节点 - 允许有，但提示）
            Set<String> targetNodes = edges.stream()
                    .map(com.demand.system.module.workflow.dto.EdgeDTO::getTarget)
                    .collect(Collectors.toSet());

            for (String nodeId : nodeIds) {
                if (!targetNodes.contains(nodeId)) {
                    errors.add("节点 '" + nodeId + "' 没有入边（可能是起始节点或孤立节点）");
                }
            }

            // 检查是否有不存在的节点引用
            for (com.demand.system.module.workflow.dto.EdgeDTO edge : edges) {
                if (!nodeIds.contains(edge.getSource())) {
                    errors.add("边的源节点 '" + edge.getSource() + "' 不存在");
                }
                if (!nodeIds.contains(edge.getTarget())) {
                    errors.add("边的目标节点 '" + edge.getTarget() + "' 不存在");
                }
            }

            // DFS 检查循环
            Map<String, List<String>> adjacency = new HashMap<>();
            for (String nodeId : nodeIds) {
                adjacency.put(nodeId, new ArrayList<>());
            }
            for (com.demand.system.module.workflow.dto.EdgeDTO edge : edges) {
                adjacency.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());
            }

            if (hasCycle(adjacency)) {
                errors.add("工作流中存在循环依赖");
            }

        } catch (Exception e) {
            errors.add("工作流定义 JSON 解析失败: " + e.getMessage());
        }

        return errors;
    }

    /**
     * 使用 DFS 检测有向图中是否存在环
     */
    private boolean hasCycle(Map<String, List<String>> adjacency) {
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();

        for (String node : adjacency.keySet()) {
            if (hasCycleDFS(node, adjacency, visited, recStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCycleDFS(String node, Map<String, List<String>> adjacency,
                                Set<String> visited, Set<String> recStack) {
        if (recStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }

        visited.add(node);
        recStack.add(node);

        List<String> neighbors = adjacency.getOrDefault(node, Collections.emptyList());
        for (String neighbor : neighbors) {
            if (hasCycleDFS(neighbor, adjacency, visited, recStack)) {
                return true;
            }
        }

        recStack.remove(node);
        return false;
    }
}
