package com.demand.system.module.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.requirement.dto.RequirementFieldAlias;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.workflow.dto.EdgeDTO;
import com.demand.system.module.workflow.dto.NodeConfigDTO;
import com.demand.system.module.workflow.dto.WorkflowDefinitionDTO;
import com.demand.system.module.workflow.engine.WorkflowVersionResolver;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Component
public class WorkflowDefinitionEngine {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowDefinitionEngine.class);

    private static final String TYPE_START_EVENT = "startEvent";
    private static final String TYPE_USER_TASK = "userTask";
    private static final String TYPE_SERVICE_TASK = "serviceTask";
    private static final String TYPE_EXCLUSIVE_GATEWAY = "exclusiveGateway";
    private static final String TYPE_END_EVENT = "endEvent";
    private static final String TYPE_STATE = "state";

    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowVersionResolver workflowVersionResolver;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final ObjectMapper objectMapper;

    public WorkflowDefinitionEngine(WorkflowVersionMapper workflowVersionMapper, WorkflowVersionResolver workflowVersionResolver,
                                  WorkflowNodeMapper workflowNodeMapper, ObjectMapper objectMapper) {
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowVersionResolver = workflowVersionResolver;
        this.workflowNodeMapper = workflowNodeMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 判断指定需求类型是否已绑定活跃工作流定义。
     * <p>新引擎 + 旧引擎（StateMachine）双轨共用。
     *
     * @param typeCode 需求类型编码
     * @return true 表示该类型有可用的 BPMN 工作流
     */
    public boolean hasActiveDefinition(String typeCode) {
        return workflowVersionResolver.findActiveVersionForType(typeCode)
                .map(version -> workflowNodeMapper.selectCount(new LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getWorkflowVersionId, version.getId())) > 0)
                .orElse(false);
    }

    /**
     * @deprecated 保留 projectId 签名兼容旧调用方，内部委托 type 维度。
     *             新代码请使用 {@link #hasActiveDefinition(String)}。
     */
    @Deprecated
    public boolean hasActiveDefinition(Long projectId) {
        // 兼容旧入口：找不到 type 信息时回退到 projectId 维度
        return workflowVersionResolver.findActiveVersion(projectId)
                .map(version -> workflowNodeMapper.selectCount(new LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getWorkflowVersionId, version.getId())) > 0)
                .orElse(false);
    }

    public List<String> validateDefinition(String definition) {
        List<String> errors = new ArrayList<>();
        WorkflowGraph graph = parseGraph(definition, errors);
        if (graph == null) {
            return errors;
        }

        if (graph.nodes().isEmpty()) {
            errors.add("工作流必须包含至少一个节点");
            return errors;
        }

        Set<String> supportedTypes = Set.of(
                TYPE_START_EVENT,
                TYPE_USER_TASK,
                TYPE_SERVICE_TASK,
                TYPE_EXCLUSIVE_GATEWAY,
                TYPE_END_EVENT,
                TYPE_STATE
        );

        for (NodeConfigDTO node : graph.orderedNodes()) {
            String nodeId = normalizeId(node.getNodeId());
            String nodeType = normalizeNodeType(node);
            if (!supportedTypes.contains(nodeType)) {
                errors.add("节点 '" + nodeId + "' 使用了不支持的类型: " + node.getType());
            }

            if (isWaitState(node) && !StringUtils.hasText(node.getName())) {
                errors.add("节点 '" + nodeId + "' 需要配置名称");
            }

            if (TYPE_START_EVENT.equals(nodeType) && !graph.incoming().getOrDefault(nodeId, List.of()).isEmpty()) {
                errors.add("startEvent 节点不能有入边: " + nodeId);
            }
            if (TYPE_END_EVENT.equals(nodeType) && !graph.outgoing().getOrDefault(nodeId, List.of()).isEmpty()) {
                errors.add("endEvent 节点不能有出边: " + nodeId);
            }
            if (TYPE_SERVICE_TASK.equals(nodeType)
                    && graph.outgoing().getOrDefault(nodeId, List.of()).size() > 1) {
                errors.add("serviceTask 节点暂不支持多条出边，请改用 exclusiveGateway: " + nodeId);
            }
            if (TYPE_EXCLUSIVE_GATEWAY.equals(nodeType)
                    && graph.outgoing().getOrDefault(nodeId, List.of()).size() < 2) {
                errors.add("exclusiveGateway 至少需要两条出边: " + nodeId);
            }
        }

        List<NodeConfigDTO> explicitStartNodes = graph.orderedNodes().stream()
                .filter(node -> TYPE_START_EVENT.equals(normalizeNodeType(node)))
                .toList();
        if (!explicitStartNodes.isEmpty() && explicitStartNodes.size() != 1) {
            errors.add("BPMN 模式下必须且只能存在一个 startEvent");
        }

        if (!explicitStartNodes.isEmpty()) {
            String startNodeId = normalizeId(explicitStartNodes.get(0).getNodeId());
            if (graph.outgoing().getOrDefault(startNodeId, List.of()).isEmpty()) {
                errors.add("startEvent 节点必须至少有一条出边");
            }
        }

        List<String> entryNodeIds = determineEntryNodeIds(graph);
        if (entryNodeIds.isEmpty()) {
            errors.add("工作流至少需要一个起始节点");
        }

        if (determineVisibleNodes(graph).isEmpty()) {
            errors.add("工作流至少需要一个可停留节点（userTask 或 endEvent）");
        }

        boolean hasEndEvent = graph.orderedNodes().stream()
                .anyMatch(node -> TYPE_END_EVENT.equals(normalizeNodeType(node)));
        if (!hasEndEvent) {
            boolean hasLegacyFinal = graph.orderedNodes().stream()
                    .anyMatch(node -> Boolean.TRUE.equals(node.getIsFinal())
                            || graph.outgoing().getOrDefault(normalizeId(node.getNodeId()), List.of()).isEmpty());
            if (!hasLegacyFinal) {
                errors.add("工作流至少需要一个终止节点");
            }
        }

        if (!entryNodeIds.isEmpty()) {
            Set<String> reachable = traverse(entryNodeIds, graph.outgoing());
            for (String nodeId : graph.nodes().keySet()) {
                if (!reachable.contains(nodeId)) {
                    errors.add("节点 '" + nodeId + "' 无法从起始节点到达");
                }
            }
        }

        for (String entryNodeId : entryNodeIds) {
            detectAutomaticCycle(graph, entryNodeId, new LinkedHashSet<>(), errors);
        }

        Set<String> runtimeNodeNames = determineVisibleNodes(graph).stream()
                .map(NodeConfigDTO::getName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (runtimeNodeNames.size() != determineVisibleNodes(graph).size()) {
            errors.add("可停留节点名称不能重复");
        }

        return errors.stream().distinct().toList();
    }

    /**
     * 按需求类型解析初始状态名。
     * <p>从 requirement.getType() 取 typeCode → 通过 WorkflowVersionResolver.resolveForType 查工作流版本。
     *
     * @param typeCode    需求类型编码（如 Requirement / Order / Bug / FEATURE）
     * @param requirement 需求实体（用于条件表达式求值）
     * @return 初始状态名；未配置工作流时返回 empty
     */
    public Optional<String> resolveInitialStateName(String typeCode, Requirement requirement) {
        Optional<WorkflowGraph> graphOptional = loadActiveGraphByType(typeCode);
        if (graphOptional.isEmpty()) {
            return Optional.empty();
        }

        WorkflowGraph graph = graphOptional.get();
        List<String> entryNodeIds = determineEntryNodeIds(graph);
        if (entryNodeIds.isEmpty()) {
            return Optional.empty();
        }

        for (String entryNodeId : entryNodeIds) {
            List<ResolvedTransitionSpec> resolved = resolveNextWaitStates(
                    graph,
                    requirement,
                    entryNodeId,
                    null,
                    null,
                    new ArrayList<>(),
                    new LinkedHashSet<>(),
                    true
            );
            if (!resolved.isEmpty()) {
                return Optional.ofNullable(resolved.get(0).targetStateName());
            }
        }
        return Optional.empty();
    }

    /**
     * @deprecated 保留 projectId 签名兼容旧调用方。新代码请使用 {@link #resolveInitialStateName(String, Requirement)}。
     */
    @Deprecated
    public Optional<String> resolveInitialStateName(Long projectId, Requirement requirement) {
        // 兼容旧入口：如果 requirement 有 type，优先走 type 维度
        if (requirement != null && StringUtils.hasText(requirement.getType())) {
            return resolveInitialStateName(requirement.getType(), requirement);
        }
        Optional<WorkflowGraph> graphOptional = loadActiveGraph(projectId);
        if (graphOptional.isEmpty()) {
            return Optional.empty();
        }

        WorkflowGraph graph = graphOptional.get();
        List<String> entryNodeIds = determineEntryNodeIds(graph);
        if (entryNodeIds.isEmpty()) {
            return Optional.empty();
        }

        for (String entryNodeId : entryNodeIds) {
            List<ResolvedTransitionSpec> resolved = resolveNextWaitStates(
                    graph,
                    requirement,
                    entryNodeId,
                    null,
                    null,
                    new ArrayList<>(),
                    new LinkedHashSet<>(),
                    true
            );
            if (!resolved.isEmpty()) {
                return Optional.ofNullable(resolved.get(0).targetStateName());
            }
        }
        return Optional.empty();
    }

    public List<ResolvedTransitionSpec> resolveAvailableTransitions(Requirement requirement) {
        if (requirement == null || !StringUtils.hasText(requirement.getStatus())) {
            return List.of();
        }

        // 优先按 type 维度加载工作流图，回退到 projectId 维度
        Optional<WorkflowGraph> graphOptional;
        if (StringUtils.hasText(requirement.getType())) {
            graphOptional = loadActiveGraphByType(requirement.getType());
        } else {
            graphOptional = loadActiveGraph(requirement.getProjectId());
        }
        if (graphOptional.isEmpty()) {
            return List.of();
        }

        WorkflowGraph graph = graphOptional.get();
        NodeConfigDTO currentNode = findVisibleNodeByName(graph, requirement.getStatus());
        if (currentNode == null) {
            return List.of();
        }

        List<ResolvedTransitionSpec> candidates = new ArrayList<>();
        String currentNodeId = normalizeId(currentNode.getNodeId());
        for (EdgeDTO edge : graph.outgoing().getOrDefault(currentNodeId, List.of())) {
            if (!conditionMatches(edge.getConditions(), requirement)) {
                continue;
            }

            candidates.addAll(resolveNextWaitStates(
                    graph,
                    requirement,
                    normalizeId(edge.getTarget()),
                    writeJson(edge.getAllowedRoles()),
                    writeJson(edge.getRequiredFields()),
                    collectConditionFragments(edge.getConditions()),
                    new LinkedHashSet<>(),
                    false
            ));
        }

        return deduplicateTransitions(candidates, currentNode.getName());
    }

    public Optional<ResolvedTransitionSpec> resolveTransition(Requirement requirement,
                                                              String fromStateName,
                                                              String targetStateName) {
        if (requirement == null || !StringUtils.hasText(fromStateName) || !StringUtils.hasText(targetStateName)) {
            return Optional.empty();
        }

        return resolveAvailableTransitions(requirement).stream()
                .filter(candidate -> Objects.equals(fromStateName, candidate.fromStateName()))
                .filter(candidate -> Objects.equals(targetStateName, candidate.targetStateName()))
                .findFirst();
    }

    public RuntimeCompilation compileRuntimeDefinition(String definition) {
        List<String> errors = validateDefinition(definition);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("；", errors));
        }

        WorkflowGraph graph = parseGraph(definition, new ArrayList<>());
        if (graph == null) {
            return new RuntimeCompilation(List.of(), List.of());
        }

        List<NodeConfigDTO> runtimeStates = determineVisibleNodes(graph);
        List<ResolvedTransitionSpec> transitions = new ArrayList<>();
        for (NodeConfigDTO runtimeState : runtimeStates) {
            String nodeId = normalizeId(runtimeState.getNodeId());
            for (EdgeDTO edge : graph.outgoing().getOrDefault(nodeId, List.of())) {
                transitions.addAll(resolveStructuralWaitStates(
                        graph,
                        runtimeState.getName(),
                        normalizeId(edge.getTarget()),
                        writeJson(edge.getAllowedRoles()),
                        writeJson(edge.getRequiredFields()),
                        collectConditionFragments(edge.getConditions()),
                        new LinkedHashSet<>()
                ));
            }
        }

        return new RuntimeCompilation(runtimeStates, deduplicateTransitions(transitions, null));
    }

    private Optional<WorkflowGraph> loadActiveGraph(Long projectId) {
        return workflowVersionResolver.findActiveVersion(projectId)
                .map(WorkflowVersion::getDefinition)
                .filter(StringUtils::hasText)
                .map(definition -> parseGraph(definition, new ArrayList<>()))
                .filter(Objects::nonNull);
    }

    /**
     * 按需求类型编码加载活跃工作流图。
     * <p>核心入口，新引擎所有公开方法均委托此方法按 type 查找工作流定义。
     */
    private Optional<WorkflowGraph> loadActiveGraphByType(String typeCode) {
        return workflowVersionResolver.findActiveVersionForType(typeCode)
                .map(WorkflowVersion::getDefinition)
                .filter(StringUtils::hasText)
                .map(definition -> parseGraph(definition, new ArrayList<>()))
                .filter(Objects::nonNull);
    }


    private WorkflowGraph parseGraph(String definition, List<String> errors) {
        if (!StringUtils.hasText(definition)) {
            errors.add("工作流定义不能为空");
            return null;
        }

        WorkflowDefinitionDTO workflow;
        try {
            workflow = objectMapper.readValue(definition, WorkflowDefinitionDTO.class);
        } catch (JsonProcessingException e) {
            errors.add("工作流定义 JSON 解析失败: " + e.getOriginalMessage());
            return null;
        }

        List<NodeConfigDTO> nodes = workflow.getNodes() == null ? List.of() : workflow.getNodes();
        List<EdgeDTO> edges = workflow.getEdges() == null ? List.of() : workflow.getEdges();

        Map<String, NodeConfigDTO> nodeMap = new LinkedHashMap<>();
        List<NodeConfigDTO> orderedNodes = new ArrayList<>();
        Set<String> nodeNames = new LinkedHashSet<>();
        for (NodeConfigDTO node : nodes) {
            if (node == null || !StringUtils.hasText(node.getNodeId())) {
                errors.add("存在未配置 nodeId 的节点");
                continue;
            }

            String nodeId = normalizeId(node.getNodeId());
            if (nodeMap.containsKey(nodeId)) {
                errors.add("节点ID重复: " + nodeId);
                continue;
            }
            if (StringUtils.hasText(node.getName()) && !nodeNames.add(node.getName().trim())) {
                errors.add("节点名称重复: " + node.getName());
            }
            nodeMap.put(nodeId, node);
            orderedNodes.add(node);
        }

        Map<String, List<EdgeDTO>> outgoing = new LinkedHashMap<>();
        Map<String, List<EdgeDTO>> incoming = new LinkedHashMap<>();
        for (String nodeId : nodeMap.keySet()) {
            outgoing.put(nodeId, new ArrayList<>());
            incoming.put(nodeId, new ArrayList<>());
        }

        for (EdgeDTO edge : edges) {
            if (edge == null) {
                continue;
            }
            String source = normalizeId(edge.getSource());
            String target = normalizeId(edge.getTarget());
            if (!StringUtils.hasText(source) || !nodeMap.containsKey(source)) {
                errors.add("边的源节点不存在: " + edge.getSource());
                continue;
            }
            if (!StringUtils.hasText(target) || !nodeMap.containsKey(target)) {
                errors.add("边的目标节点不存在: " + edge.getTarget());
                continue;
            }
            outgoing.get(source).add(edge);
            incoming.get(target).add(edge);
        }

        if (!errors.isEmpty()) {
            return null;
        }

        orderedNodes.sort(Comparator
                .comparing(NodeConfigDTO::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(NodeConfigDTO::getNodeId, Comparator.nullsLast(String::compareTo)));

        return new WorkflowGraph(workflow, nodeMap, orderedNodes, outgoing, incoming);
    }

    private List<NodeConfigDTO> determineVisibleNodes(WorkflowGraph graph) {
        return graph.orderedNodes().stream()
                .filter(this::isWaitState)
                .toList();
    }

    private List<String> determineEntryNodeIds(WorkflowGraph graph) {
        List<String> explicitStartNodes = graph.orderedNodes().stream()
                .filter(node -> TYPE_START_EVENT.equals(normalizeNodeType(node)))
                .map(NodeConfigDTO::getNodeId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (!explicitStartNodes.isEmpty()) {
            return explicitStartNodes;
        }

        List<String> inferredStarts = graph.nodes().keySet().stream()
                .filter(nodeId -> graph.incoming().getOrDefault(nodeId, List.of()).isEmpty())
                .toList();
        if (!inferredStarts.isEmpty()) {
            return inferredStarts;
        }

        return graph.orderedNodes().stream()
                .findFirst()
                .map(NodeConfigDTO::getNodeId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .stream()
                .toList();
    }

    private Set<String> traverse(Collection<String> starts, Map<String, List<EdgeDTO>> outgoing) {
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(starts);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            for (EdgeDTO edge : outgoing.getOrDefault(current, List.of())) {
                String target = normalizeId(edge.getTarget());
                if (StringUtils.hasText(target) && !visited.contains(target)) {
                    queue.offer(target);
                }
            }
        }
        return visited;
    }

    private void detectAutomaticCycle(WorkflowGraph graph,
                                      String currentNodeId,
                                      Set<String> currentPath,
                                      List<String> errors) {
        NodeConfigDTO currentNode = graph.nodes().get(currentNodeId);
        if (currentNode == null || isWaitState(currentNode)) {
            return;
        }
        if (!currentPath.add(currentNodeId)) {
            errors.add("自动推进节点之间存在循环，请调整节点 '" + currentNodeId + "'");
            return;
        }

        for (EdgeDTO edge : graph.outgoing().getOrDefault(currentNodeId, List.of())) {
            detectAutomaticCycle(graph, normalizeId(edge.getTarget()), currentPath, errors);
        }
        currentPath.remove(currentNodeId);
    }

    private List<ResolvedTransitionSpec> resolveNextWaitStates(WorkflowGraph graph,
                                                               Requirement requirement,
                                                               String nodeId,
                                                               String allowedRolesJson,
                                                               String requiredFieldsJson,
                                                               List<String> accumulatedConditions,
                                                               Set<String> automaticPath,
                                                               boolean initialFlow) {
        NodeConfigDTO node = graph.nodes().get(nodeId);
        if (node == null) {
            return List.of();
        }

        if (isWaitState(node)) {
            if (initialFlow && !StringUtils.hasText(node.getName())) {
                return List.of();
            }
            return List.of(new ResolvedTransitionSpec(
                    null,
                    node.getName(),
                    allowedRolesJson,
                    requiredFieldsJson,
                    combineConditions(accumulatedConditions)
            ));
        }

        if (!automaticPath.add(nodeId)) {
            log.warn("Skip recursive automatic workflow path on node {}", nodeId);
            return List.of();
        }

        List<ResolvedTransitionSpec> results = switch (normalizeNodeType(node)) {
            case TYPE_EXCLUSIVE_GATEWAY -> resolveGateway(graph, requirement, nodeId, allowedRolesJson,
                    requiredFieldsJson, accumulatedConditions, automaticPath);
            case TYPE_SERVICE_TASK, TYPE_START_EVENT -> resolveLinearAutomaticNode(graph, requirement, nodeId,
                    allowedRolesJson, requiredFieldsJson, accumulatedConditions, automaticPath);
            default -> List.of();
        };

        automaticPath.remove(nodeId);
        return results;
    }

    private List<ResolvedTransitionSpec> resolveGateway(WorkflowGraph graph,
                                                        Requirement requirement,
                                                        String nodeId,
                                                        String allowedRolesJson,
                                                        String requiredFieldsJson,
                                                        List<String> accumulatedConditions,
                                                        Set<String> automaticPath) {
        List<EdgeDTO> outgoing = graph.outgoing().getOrDefault(nodeId, List.of());
        if (outgoing.isEmpty()) {
            return List.of();
        }

        EdgeDTO defaultEdge = outgoing.stream()
                .filter(edge -> Boolean.TRUE.equals(edge.getDefaultFlow()) || !StringUtils.hasText(edge.getConditions()))
                .findFirst()
                .orElse(null);

        for (EdgeDTO edge : outgoing) {
            if (Boolean.TRUE.equals(edge.getDefaultFlow())) {
                continue;
            }
            if (conditionMatches(edge.getConditions(), requirement)) {
                List<String> nextConditions = appendCondition(accumulatedConditions, edge.getConditions());
                return resolveNextWaitStates(graph, requirement, normalizeId(edge.getTarget()), allowedRolesJson,
                        requiredFieldsJson, nextConditions, automaticPath, false);
            }
        }

        if (defaultEdge == null) {
            return List.of();
        }

        List<String> nextConditions = appendCondition(accumulatedConditions, defaultEdge.getConditions());
        return resolveNextWaitStates(graph, requirement, normalizeId(defaultEdge.getTarget()), allowedRolesJson,
                requiredFieldsJson, nextConditions, automaticPath, false);
    }

    private List<ResolvedTransitionSpec> resolveLinearAutomaticNode(WorkflowGraph graph,
                                                                    Requirement requirement,
                                                                    String nodeId,
                                                                    String allowedRolesJson,
                                                                    String requiredFieldsJson,
                                                                    List<String> accumulatedConditions,
                                                                    Set<String> automaticPath) {
        List<EdgeDTO> outgoing = graph.outgoing().getOrDefault(nodeId, List.of());
        List<ResolvedTransitionSpec> results = new ArrayList<>();
        for (EdgeDTO edge : outgoing) {
            if (!conditionMatches(edge.getConditions(), requirement)) {
                continue;
            }
            List<String> nextConditions = appendCondition(accumulatedConditions, edge.getConditions());
            results.addAll(resolveNextWaitStates(graph, requirement, normalizeId(edge.getTarget()), allowedRolesJson,
                    requiredFieldsJson, nextConditions, automaticPath, false));
        }
        return results;
    }

    private List<ResolvedTransitionSpec> resolveStructuralWaitStates(WorkflowGraph graph,
                                                                     String fromStateName,
                                                                     String nodeId,
                                                                     String allowedRolesJson,
                                                                     String requiredFieldsJson,
                                                                     List<String> accumulatedConditions,
                                                                     Set<String> automaticPath) {
        NodeConfigDTO node = graph.nodes().get(nodeId);
        if (node == null) {
            return List.of();
        }
        if (isWaitState(node)) {
            return List.of(new ResolvedTransitionSpec(
                    fromStateName,
                    node.getName(),
                    allowedRolesJson,
                    requiredFieldsJson,
                    combineConditions(accumulatedConditions)
            ));
        }

        if (!automaticPath.add(nodeId)) {
            return List.of();
        }

        List<ResolvedTransitionSpec> results = new ArrayList<>();
        for (EdgeDTO edge : graph.outgoing().getOrDefault(nodeId, List.of())) {
            List<String> nextConditions = appendCondition(accumulatedConditions, edge.getConditions());
            results.addAll(resolveStructuralWaitStates(graph, fromStateName, normalizeId(edge.getTarget()),
                    allowedRolesJson, requiredFieldsJson, nextConditions, automaticPath));
        }

        automaticPath.remove(nodeId);
        return results;
    }

    private List<ResolvedTransitionSpec> deduplicateTransitions(List<ResolvedTransitionSpec> transitions,
                                                                String forcedFromStateName) {
        Map<String, ResolvedTransitionSpec> deduplicated = new LinkedHashMap<>();
        for (ResolvedTransitionSpec transition : transitions) {
            String fromStateName = StringUtils.hasText(forcedFromStateName) ? forcedFromStateName : transition.fromStateName();
            if (!StringUtils.hasText(fromStateName) || !StringUtils.hasText(transition.targetStateName())) {
                continue;
            }
            String key = fromStateName + "->" + transition.targetStateName();
            deduplicated.putIfAbsent(key, new ResolvedTransitionSpec(
                    fromStateName,
                    transition.targetStateName(),
                    transition.allowedRolesJson(),
                    transition.requiredFieldsJson(),
                    transition.conditionsJson()
            ));
        }
        return new ArrayList<>(deduplicated.values());
    }

    private NodeConfigDTO findVisibleNodeByName(WorkflowGraph graph, String stateName) {
        if (!StringUtils.hasText(stateName)) {
            return null;
        }
        return determineVisibleNodes(graph).stream()
                .filter(node -> stateName.equals(node.getName()))
                .findFirst()
                .orElse(null);
    }

    private boolean isWaitState(NodeConfigDTO node) {
        String nodeType = normalizeNodeType(node);
        return TYPE_USER_TASK.equals(nodeType) || TYPE_END_EVENT.equals(nodeType) || TYPE_STATE.equals(nodeType);
    }

    private String normalizeNodeType(NodeConfigDTO node) {
        if (node == null || !StringUtils.hasText(node.getType())) {
            return TYPE_USER_TASK;
        }

        return switch (node.getType().trim()) {
            case "start", TYPE_START_EVENT -> TYPE_START_EVENT;
            case "state", TYPE_USER_TASK -> TYPE_USER_TASK;
            case "service", TYPE_SERVICE_TASK -> TYPE_SERVICE_TASK;
            case "gateway", TYPE_EXCLUSIVE_GATEWAY -> TYPE_EXCLUSIVE_GATEWAY;
            case "end", TYPE_END_EVENT -> TYPE_END_EVENT;
            default -> node.getType().trim();
        };
    }

    private boolean conditionMatches(String rawCondition, Requirement requirement) {
        if (!StringUtils.hasText(rawCondition) || "{}".equals(rawCondition.trim())) {
            return true;
        }

        try {
            JsonNode root = objectMapper.readTree(rawCondition);
            return evaluateCondition(root, requirement);
        } catch (Exception e) {
            log.warn("Ignore malformed workflow condition: {}", rawCondition);
            return false;
        }
    }

    private boolean evaluateCondition(JsonNode node, Requirement requirement) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return true;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (!evaluateCondition(child, requirement)) {
                    return false;
                }
            }
            return true;
        }
        if (!node.isObject()) {
            return node.asBoolean(true);
        }
        if (node.isEmpty()) {
            return true;
        }

        if (node.has("all")) {
            for (JsonNode child : node.get("all")) {
                if (!evaluateCondition(child, requirement)) {
                    return false;
                }
            }
            return true;
        }
        if (node.has("any")) {
            for (JsonNode child : node.get("any")) {
                if (evaluateCondition(child, requirement)) {
                    return true;
                }
            }
            return false;
        }
        if (node.has("none")) {
            for (JsonNode child : node.get("none")) {
                if (evaluateCondition(child, requirement)) {
                    return false;
                }
            }
            return true;
        }

        String field = RequirementFieldAlias.normalize(textValue(node, "field"));
        if (!StringUtils.hasText(field)) {
            return true;
        }

        Object left = resolveRequirementField(requirement, field);
        String operator = Optional.ofNullable(textValue(node, "operator"))
                .orElse(Optional.ofNullable(textValue(node, "op")).orElse("EQ"))
                .trim()
                .toUpperCase();
        JsonNode rightNode = node.get("value");

        return switch (operator) {
            case "EQ", "=" -> compareEquals(left, rightNode);
            case "NE", "!=", "NOT_EQ" -> !compareEquals(left, rightNode);
            case "IN" -> compareIn(left, rightNode);
            case "NOT_IN" -> !compareIn(left, rightNode);
            case "GT" -> compareNumber(left, rightNode) > 0;
            case "GTE", ">=" -> compareNumber(left, rightNode) >= 0;
            case "LT" -> compareNumber(left, rightNode) < 0;
            case "LTE", "<=" -> compareNumber(left, rightNode) <= 0;
            case "IS_NULL" -> left == null;
            case "NOT_NULL" -> left != null;
            case "CONTAINS" -> left != null && rightNode != null && left.toString().contains(rightNode.asText());
            default -> false;
        };
    }

    private Object resolveRequirementField(Requirement requirement, String field) {
        if (requirement == null) {
            return null;
        }
        return switch (field) {
            case "id" -> requirement.getId();
            case "projectId" -> requirement.getProjectId();
            case "title" -> requirement.getTitle();
            case "description" -> requirement.getDescription();
            case "type" -> requirement.getType();
            case "priority" -> requirement.getPriority();
            case "status" -> requirement.getStatus();
            case "creatorId" -> requirement.getCreatorId();
            case "assigneeId" -> requirement.getAssigneeId();
            case "moduleId" -> requirement.getModuleId();
            case "iterationId" -> requirement.getIterationId();
            case "startDate" -> requirement.getStartDate();
            case "dueDate" -> requirement.getDueDate();
            case "estimatedHours" -> requirement.getEstimatedHours();
            case "actualHours" -> requirement.getActualHours();
            case "attachments" -> requirement.getAttachments();
            case "createdAt" -> requirement.getCreatedAt();
            case "updatedAt" -> requirement.getUpdatedAt();
            default -> null;
        };
    }

    private boolean compareEquals(Object left, JsonNode rightNode) {
        if (left == null) {
            return rightNode == null || rightNode.isNull();
        }
        if (rightNode == null || rightNode.isNull()) {
            return false;
        }
        if (left instanceof Number || left instanceof BigDecimal) {
            return compareNumber(left, rightNode) == 0;
        }
        if (left instanceof LocalDate leftDate) {
            return Objects.equals(leftDate, parseLocalDate(rightNode.asText(null)));
        }
        if (left instanceof LocalDateTime leftDateTime) {
            return Objects.equals(leftDateTime, parseLocalDateTime(rightNode.asText(null)));
        }
        return Objects.equals(String.valueOf(left), rightNode.asText());
    }

    private boolean compareIn(Object left, JsonNode rightNode) {
        if (left == null || rightNode == null || !rightNode.isArray()) {
            return false;
        }
        for (JsonNode child : rightNode) {
            if (compareEquals(left, child)) {
                return true;
            }
        }
        return false;
    }

    private int compareNumber(Object left, JsonNode rightNode) {
        BigDecimal leftValue = toBigDecimal(left);
        BigDecimal rightValue = rightNode == null || rightNode.isNull() ? null : toBigDecimal(rightNode.asText());
        if (leftValue == null || rightValue == null) {
            return -1;
        }
        return leftValue.compareTo(rightValue);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseLocalDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private List<String> collectConditionFragments(String rawCondition) {
        if (!StringUtils.hasText(rawCondition) || "{}".equals(rawCondition.trim())) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Collections.singletonList(rawCondition));
    }

    private List<String> appendCondition(List<String> conditions, String rawCondition) {
        List<String> combined = new ArrayList<>(conditions);
        if (StringUtils.hasText(rawCondition) && !"{}".equals(rawCondition.trim())) {
            combined.add(rawCondition);
        }
        return combined;
    }

    private String combineConditions(List<String> conditions) {
        List<String> normalized = conditions.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return "{}";
        }
        if (normalized.size() == 1) {
            return normalized.get(0);
        }
        try {
            List<JsonNode> nodes = new ArrayList<>();
            for (String condition : normalized) {
                nodes.add(objectMapper.readTree(condition));
            }
            Map<String, Object> wrapper = Map.of("all", nodes);
            return objectMapper.writeValueAsString(wrapper);
        } catch (Exception e) {
            return normalized.get(0);
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection && collection.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private String normalizeId(String id) {
        return id == null ? null : id.trim();
    }

    private record WorkflowGraph(WorkflowDefinitionDTO workflow,
                                 Map<String, NodeConfigDTO> nodes,
                                 List<NodeConfigDTO> orderedNodes,
                                 Map<String, List<EdgeDTO>> outgoing,
                                 Map<String, List<EdgeDTO>> incoming) {
    }

    public record ResolvedTransitionSpec(String fromStateName,
                                         String targetStateName,
                                         String allowedRolesJson,
                                         String requiredFieldsJson,
                                         String conditionsJson) {
    }

    public record RuntimeCompilation(List<NodeConfigDTO> runtimeStates,
                                     List<ResolvedTransitionSpec> transitions) {
    }
}
