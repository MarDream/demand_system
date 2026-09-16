package com.demand.system.module.workflow.support;

import com.demand.system.module.auth.entity.SysUser;
import com.demand.system.module.auth.mapper.SysUserMapper;
import com.demand.system.module.knowledge.entity.KnowledgeBase;
import com.demand.system.module.knowledge.mapper.KnowledgeBaseMapper;
import com.demand.system.module.organization.entity.SysOrg;
import com.demand.system.module.organization.mapper.SysOrgMapper;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.RoleGroup;
import com.demand.system.module.rbac.mapper.RoleGroupMapper;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.workflow.dto.WorkflowEdgeDTO;
import com.demand.system.module.workflow.dto.WorkflowNodeDTO;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作流配置变更日志构建器：对比新旧节点/连线/元数据，产出结构化 diff，
 * 以 JSON 写入 workflow_history.change_log，供前端时间线展示「本次较上次改了什么」。
 * 结构：{"summary":"新增 1 个节点、修改 2 个节点","items":[{scope,change,key,name,fields:[{field,label,oldValue,newValue}]}]}
 */
@Component
public class WorkflowChangeLogBuilder {

    private static final Map<String, String> NODE_TYPE_LABELS = Map.of(
            "start", "开始", "approval", "审批", "cc", "抄送",
            "condition", "条件", "parallel", "并行", "end", "结束");

    private static final Map<String, String> ASSIGNEE_TYPE_LABELS = Map.of(
            "SPECIFIED_USER", "指定用户", "SPECIFIED_ROLE", "指定角色", "SPECIFIED_ROLE_GROUP", "指定角色组",
            "SPECIFIED_ORG", "指定组织", "CREATOR", "提交人", "PREV_APPROVER", "上一节点处理人");

    private static final Map<String, String> TIMEOUT_ACTION_LABELS = Map.of(
            "AUTO_APPROVE", "自动通过", "AUTO_REJECT", "自动拒绝", "ESCALATE", "转交上级");

    /** 节点 properties 中已知键的中文标签；未识别的键原样展示 */
    private static final Map<String, String> PROPERTY_LABELS = Map.ofEntries(
            Map.entry("nodeStatusCode", "节点状态"),
            Map.entry("allowCancel", "允许取消"),
            Map.entry("projectRequired", "项目必选"),
            Map.entry("requireAttachment", "必须上传附件"),
            Map.entry("allowModifyType", "允许变更工单类型"),
            Map.entry("notifyOnEnter", "消息提醒"),
            Map.entry("notifyScope", "提醒范围"),
            Map.entry("ratingConfig", "节点评价"),
            Map.entry("ccMode", "抄送方式"),
            Map.entry("fieldPermissions", "动态字段权限"),
            Map.entry("conditionDesc", "条件描述"));

    /** 仅前端交互使用的瞬态属性，不纳入 diff */
    private static final Collection<String> TRANSIENT_PROPERTY_KEYS = List.of("selectedNextNode");

    private static final Map<String, String> NOTIFY_SCOPE_LABELS = Map.of(
            "PATH_APPROVERS", "已审批节点路径上的用户（含创建人）",
            "ACTUAL_HANDLERS", "从需求创建到当前节点实际处理过的用户");

    private static final Map<String, String> CC_MODE_LABELS = Map.of(
            "MESSAGE", "站内消息", "READ_ONLY_TODO", "只读查阅待办");

    private static final Map<String, String> TERMINAL_NODE_LABELS = Map.of(
            "cancelled", "已取消", "accepted", "已通过", "rejected", "已拒绝");

    private final SysUserMapper sysUserMapper;
    private final RoleMapper roleMapper;
    private final RoleGroupMapper roleGroupMapper;
    private final SysOrgMapper sysOrgMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ObjectMapper objectMapper;

    public WorkflowChangeLogBuilder(SysUserMapper sysUserMapper, RoleMapper roleMapper,
                                    RoleGroupMapper roleGroupMapper, SysOrgMapper sysOrgMapper,
                                    KnowledgeBaseMapper knowledgeBaseMapper, ObjectMapper objectMapper) {
        this.sysUserMapper = sysUserMapper;
        this.roleMapper = roleMapper;
        this.roleGroupMapper = roleGroupMapper;
        this.sysOrgMapper = sysOrgMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.objectMapper = objectMapper;
    }

    /** 单条字段级变更 */
    public record FieldChange(String field, String label, String oldValue, String newValue) {}

    /** 单条变更项：scope=node/edge/meta，change=added/removed/modified */
    public record ChangeItem(String scope, String change, String key, String name, List<FieldChange> fields) {}

    /** diff 结果：summary 为人类可读摘要，json 为完整 change_log JSON */
    public record ChangeLogResult(String summary, String json) {}

    /**
     * 对比新旧配置产出变更日志。oldNodes/oldEdges 为数据库中的旧配置（节点/连线 ID 带 v{versionId}_ 前缀），
     * newNodes/newEdges 为本次保存的逻辑配置。旧配置为空时视为首次创建（全部记为新增）。
     */
    public ChangeLogResult diff(Long versionId,
                                List<WorkflowNode> oldNodes, List<WorkflowEdge> oldEdges,
                                List<WorkflowNodeDTO> newNodes, List<WorkflowEdgeDTO> newEdges,
                                WorkflowVersion oldVersion, WorkflowVersion newVersion) {
        String prefix = "v" + versionId + "_";

        Map<String, WorkflowNode> oldNodeMap = new LinkedHashMap<>();
        if (oldNodes != null) {
            for (WorkflowNode n : oldNodes) {
                oldNodeMap.put(stripPrefix(n.getNodeId(), prefix), n);
            }
        }
        Map<String, WorkflowEdge> oldEdgeMap = new LinkedHashMap<>();
        if (oldEdges != null) {
            for (WorkflowEdge e : oldEdges) {
                oldEdgeMap.put(stripPrefix(e.getEdgeId(), prefix), e);
            }
        }

        Map<String, String> nodeNameMap = buildNodeNameMap(oldNodeMap, oldEdges, prefix);
        nodeNameMap.putAll(buildNodeNameMap(newNodes, newEdges, null));

        List<ChangeItem> items = new ArrayList<>();
        diffNodes(oldNodeMap, newNodes, items);
        diffEdges(oldEdgeMap, newEdges, prefix, nodeNameMap, items);
        diffMeta(oldVersion, newVersion, items);

        return buildResult(items);
    }

    // ==== 节点 diff ====

    private void diffNodes(Map<String, WorkflowNode> oldNodeMap, List<WorkflowNodeDTO> newNodes,
                           List<ChangeItem> items) {
        Map<String, WorkflowNodeDTO> newNodeMap = new LinkedHashMap<>();
        if (newNodes != null) {
            for (WorkflowNodeDTO dto : newNodes) {
                if (StringUtils.hasText(dto.getNodeId())) {
                    newNodeMap.put(dto.getNodeId(), dto);
                }
            }
        }

        for (Map.Entry<String, WorkflowNodeDTO> entry : newNodeMap.entrySet()) {
            String key = entry.getKey();
            WorkflowNodeDTO next = entry.getValue();
            WorkflowNode prev = oldNodeMap.get(key);
            if (prev == null) {
                items.add(new ChangeItem("node", "added", key, displayName(next.getNodeType(), next.getNodeName()), null));
            } else {
                List<FieldChange> fields = diffNodeFields(prev, next);
                if (!fields.isEmpty()) {
                    items.add(new ChangeItem("node", "modified", key, displayName(next.getNodeType(), next.getNodeName()), fields));
                }
            }
        }
        for (Map.Entry<String, WorkflowNode> entry : oldNodeMap.entrySet()) {
            if (!newNodeMap.containsKey(entry.getKey())) {
                items.add(new ChangeItem("node", "removed", entry.getKey(),
                        displayName(entry.getValue().getNodeType(), entry.getValue().getNodeName()), null));
            }
        }
    }

    private List<FieldChange> diffNodeFields(WorkflowNode prev, WorkflowNodeDTO next) {
        List<FieldChange> fields = new ArrayList<>();

        if (!Objects.equals(prev.getNodeName(), next.getNodeName())) {
            fields.add(new FieldChange("nodeName", "节点名称", prev.getNodeName(), next.getNodeName()));
        }
        if (!Objects.equals(prev.getNodeType(), next.getNodeType())) {
            fields.add(new FieldChange("nodeType", "节点类型",
                    nodeTypeLabel(prev.getNodeType()), nodeTypeLabel(next.getNodeType())));
        }

        String prevAssignee = formatAssignee(prev.getAssigneeType(), prev.getAssigneeRoleId(),
                prev.getAssigneeRoleGroupId(), prev.getAssigneeOrgId(), prev.getAssigneeUserIds());
        String nextAssignee = formatAssignee(next.getAssigneeType(), next.getAssigneeRoleId(),
                next.getAssigneeRoleGroupId(), next.getAssigneeOrgId(), next.getAssigneeUserIds());
        if (!Objects.equals(prevAssignee, nextAssignee)) {
            fields.add(new FieldChange("assignee", "处理人", prevAssignee, nextAssignee));
        }

        if (!Objects.equals(prev.getTimeoutHours(), next.getTimeoutHours())) {
            fields.add(new FieldChange("timeoutHours", "超时时间（小时）",
                    formatTimeout(prev.getTimeoutHours()), formatTimeout(next.getTimeoutHours())));
        }
        if (!Objects.equals(prev.getTimeoutAction(), next.getTimeoutAction())) {
            fields.add(new FieldChange("timeoutAction", "超时动作",
                    timeoutActionLabel(prev.getTimeoutAction()), timeoutActionLabel(next.getTimeoutAction())));
        }

        diffProperties(prev.getProperties(), next.getProperties(), fields);
        return fields;
    }

    private void diffProperties(Map<String, Object> prevProps, Map<String, Object> nextProps,
                                List<FieldChange> fields) {
        Map<String, Object> prev = prevProps == null ? Map.of() : prevProps;
        Map<String, Object> next = nextProps == null ? Map.of() : nextProps;
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(prev.keySet());
        keys.addAll(next.keySet());
        for (String key : keys) {
            if (TRANSIENT_PROPERTY_KEYS.contains(key)) {
                continue;
            }
            String prevValue = formatPropertyValue(prev.get(key));
            String nextValue = formatPropertyValue(next.get(key));
            if (!Objects.equals(prevValue, nextValue)) {
                fields.add(new FieldChange("properties." + key,
                        PROPERTY_LABELS.getOrDefault(key, key), prevValue, nextValue));
            }
        }
    }

    // ==== 连线 diff ====

    private void diffEdges(Map<String, WorkflowEdge> oldEdgeMap, List<WorkflowEdgeDTO> newEdges,
                           String prefix, Map<String, String> nodeNameMap, List<ChangeItem> items) {
        Map<String, WorkflowEdgeDTO> newEdgeMap = new LinkedHashMap<>();
        if (newEdges != null) {
            for (WorkflowEdgeDTO dto : newEdges) {
                if (StringUtils.hasText(dto.getEdgeId())) {
                    newEdgeMap.put(dto.getEdgeId(), dto);
                }
            }
        }

        for (Map.Entry<String, WorkflowEdgeDTO> entry : newEdgeMap.entrySet()) {
            String key = entry.getKey();
            WorkflowEdgeDTO next = entry.getValue();
            WorkflowEdge prev = oldEdgeMap.get(key);
            String name = edgeDisplayName(next.getSourceNodeId(), next.getTargetNodeId(), nodeNameMap, prefix);
            if (prev == null) {
                items.add(new ChangeItem("edge", "added", key, name, null));
            } else {
                List<FieldChange> fields = diffEdgeFields(prev, next, nodeNameMap, prefix);
                if (!fields.isEmpty()) {
                    items.add(new ChangeItem("edge", "modified", key, name, fields));
                }
            }
        }
        for (Map.Entry<String, WorkflowEdge> entry : oldEdgeMap.entrySet()) {
            if (!newEdgeMap.containsKey(entry.getKey())) {
                WorkflowEdge prev = entry.getValue();
                items.add(new ChangeItem("edge", "removed", entry.getKey(),
                        edgeDisplayName(prev.getSourceNodeId(), prev.getTargetNodeId(), nodeNameMap, prefix), null));
            }
        }
    }

    private List<FieldChange> diffEdgeFields(WorkflowEdge prev, WorkflowEdgeDTO next,
                                             Map<String, String> nodeNameMap, String prefix) {
        List<FieldChange> fields = new ArrayList<>();
        String prevPath = edgeDisplayName(prev.getSourceNodeId(), prev.getTargetNodeId(), nodeNameMap, prefix);
        String nextPath = edgeDisplayName(next.getSourceNodeId(), next.getTargetNodeId(), nodeNameMap, prefix);
        if (!Objects.equals(prevPath, nextPath)) {
            fields.add(new FieldChange("path", "流转路径", prevPath, nextPath));
        }
        if (!Objects.equals(prev.getLabel(), next.getLabel())) {
            fields.add(new FieldChange("label", "连线标签", prev.getLabel(), next.getLabel()));
        }
        String prevCondition = formatCondition(prev.getCondition());
        String nextCondition = formatCondition(next.getCondition());
        if (!Objects.equals(prevCondition, nextCondition)) {
            fields.add(new FieldChange("condition", "流转条件", prevCondition, nextCondition));
        }
        return fields;
    }

    // ==== 元数据 diff ====

    private void diffMeta(WorkflowVersion oldVersion, WorkflowVersion newVersion, List<ChangeItem> items) {
        if (oldVersion == null || newVersion == null) {
            return;
        }
        List<FieldChange> fields = new ArrayList<>();
        if (!Objects.equals(oldVersion.getName(), newVersion.getName())) {
            fields.add(new FieldChange("name", "流程名称", oldVersion.getName(), newVersion.getName()));
        }
        if (!Objects.equals(oldVersion.getVersion(), newVersion.getVersion())) {
            fields.add(new FieldChange("version", "版本号", oldVersion.getVersion(), newVersion.getVersion()));
        }
        if (!Objects.equals(oldVersion.getKnowledgeBaseId(), newVersion.getKnowledgeBaseId())) {
            fields.add(new FieldChange("knowledgeBaseId", "知识库绑定",
                    knowledgeBaseName(oldVersion.getKnowledgeBaseId()), knowledgeBaseName(newVersion.getKnowledgeBaseId())));
        }
        if (!fields.isEmpty()) {
            items.add(new ChangeItem("meta", "modified", "meta", "版本元数据", fields));
        }
    }

    // ==== 汇总 ====

    private ChangeLogResult buildResult(List<ChangeItem> items) {
        long addedNodes = items.stream().filter(i -> "node".equals(i.scope()) && "added".equals(i.change())).count();
        long removedNodes = items.stream().filter(i -> "node".equals(i.scope()) && "removed".equals(i.change())).count();
        long modifiedNodes = items.stream().filter(i -> "node".equals(i.scope()) && "modified".equals(i.change())).count();
        long addedEdges = items.stream().filter(i -> "edge".equals(i.scope()) && "added".equals(i.change())).count();
        long removedEdges = items.stream().filter(i -> "edge".equals(i.scope()) && "removed".equals(i.change())).count();
        long modifiedEdges = items.stream().filter(i -> "edge".equals(i.scope()) && "modified".equals(i.change())).count();
        long metaChanges = items.stream().filter(i -> "meta".equals(i.scope())).count();

        List<String> parts = new ArrayList<>();
        if (addedNodes > 0) parts.add("新增 " + addedNodes + " 个节点");
        if (removedNodes > 0) parts.add("删除 " + removedNodes + " 个节点");
        if (modifiedNodes > 0) parts.add("修改 " + modifiedNodes + " 个节点");
        if (addedEdges > 0) parts.add("新增 " + addedEdges + " 条连线");
        if (removedEdges > 0) parts.add("删除 " + removedEdges + " 条连线");
        if (modifiedEdges > 0) parts.add("修改 " + modifiedEdges + " 条连线");
        if (metaChanges > 0) parts.add("修改版本信息");

        String summary = String.join("、", parts);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("items", items);
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            json = null;
        }
        return new ChangeLogResult(summary, json);
    }

    // ==== 展示格式化 ====

    private Map<String, String> buildNodeNameMap(Map<String, WorkflowNode> oldNodeMap,
                                                 List<WorkflowEdge> oldEdges, String prefix) {
        Map<String, String> map = new HashMap<>();
        oldNodeMap.values().forEach(n -> map.put(stripPrefix(n.getNodeId(), prefix), n.getNodeName()));
        if (oldEdges != null) {
            for (WorkflowEdge e : oldEdges) {
                putTerminalName(map, e.getSourceNodeId(), prefix);
                putTerminalName(map, e.getTargetNodeId(), prefix);
            }
        }
        return map;
    }

    private Map<String, String> buildNodeNameMap(List<WorkflowNodeDTO> nodes, List<WorkflowEdgeDTO> edges, String prefix) {
        Map<String, String> map = new HashMap<>();
        if (nodes != null) {
            for (WorkflowNodeDTO n : nodes) {
                if (StringUtils.hasText(n.getNodeId())) {
                    map.put(n.getNodeId(), n.getNodeName());
                }
            }
        }
        if (edges != null) {
            for (WorkflowEdgeDTO e : edges) {
                putTerminalName(map, e.getSourceNodeId(), prefix);
                putTerminalName(map, e.getTargetNodeId(), prefix);
            }
        }
        return map;
    }

    private void putTerminalName(Map<String, String> map, String nodeId, String prefix) {
        if (StringUtils.hasText(nodeId) && !map.containsKey(stripPrefix(nodeId, prefix))) {
            String label = TERMINAL_NODE_LABELS.get(nodeId.toLowerCase());
            if (label != null) {
                map.put(nodeId, label);
            }
        }
    }

    private String edgeDisplayName(String sourceNodeId, String targetNodeId,
                                   Map<String, String> nodeNameMap, String prefix) {
        String source = nodeNameMap.getOrDefault(stripPrefix(sourceNodeId, prefix), stripPrefix(sourceNodeId, prefix));
        String target = nodeNameMap.getOrDefault(stripPrefix(targetNodeId, prefix), stripPrefix(targetNodeId, prefix));
        return source + " → " + target;
    }

    private String formatAssignee(String assigneeType, Integer roleId, Long roleGroupId, Long orgId, List<Long> userIds) {
        if (!StringUtils.hasText(assigneeType)) {
            return null;
        }
        String typeLabel = ASSIGNEE_TYPE_LABELS.getOrDefault(assigneeType, assigneeType);
        String detail = switch (assigneeType) {
            case "SPECIFIED_ROLE" -> roleId == null ? null : roleName(roleId);
            case "SPECIFIED_ROLE_GROUP" -> roleGroupId == null ? null : roleGroupName(roleGroupId);
            case "SPECIFIED_ORG" -> orgId == null ? null : orgName(orgId);
            case "SPECIFIED_USER" -> userIds == null || userIds.isEmpty() ? null : userNames(userIds);
            default -> null;
        };
        return detail == null ? typeLabel : typeLabel + "：" + detail;
    }

    private String userNames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return null;
        }
        try {
            Map<Long, String> names = new HashMap<>();
            for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
                names.put(user.getId(), StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
            }
            return userIds.stream().map(id -> names.getOrDefault(id, "用户#" + id))
                    .collect(Collectors.joining("、"));
        } catch (Exception e) {
            return userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
    }

    private String roleName(Integer roleId) {
        try {
            Role role = roleMapper.selectById(roleId.longValue());
            return role != null && StringUtils.hasText(role.getName()) ? role.getName() : "角色#" + roleId;
        } catch (Exception e) {
            return "角色#" + roleId;
        }
    }

    private String roleGroupName(Long roleGroupId) {
        try {
            RoleGroup group = roleGroupMapper.selectById(roleGroupId);
            return group != null && StringUtils.hasText(group.getName()) ? group.getName() : "角色组#" + roleGroupId;
        } catch (Exception e) {
            return "角色组#" + roleGroupId;
        }
    }

    private String orgName(Long orgId) {
        try {
            SysOrg org = sysOrgMapper.selectById(orgId);
            return org != null && StringUtils.hasText(org.getName()) ? org.getName() : "组织#" + orgId;
        } catch (Exception e) {
            return "组织#" + orgId;
        }
    }

    private String knowledgeBaseName(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return "未绑定";
        }
        try {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
            return kb != null && StringUtils.hasText(kb.getName()) ? kb.getName() : "知识库#" + knowledgeBaseId;
        } catch (Exception e) {
            return "知识库#" + knowledgeBaseId;
        }
    }

    private String formatPropertyValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b ? "开启" : "关闭";
        }
        if (value instanceof String s) {
            return StringUtils.hasText(NOTIFY_SCOPE_LABELS.get(s)) ? NOTIFY_SCOPE_LABELS.get(s)
                    : (StringUtils.hasText(CC_MODE_LABELS.get(s)) ? CC_MODE_LABELS.get(s) : s);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(v -> formatPropertyValue(v)).collect(Collectors.joining("、"));
        }
        if (value instanceof Map) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                return String.valueOf(value);
            }
        }
        String text = String.valueOf(value);
        return NOTIFY_SCOPE_LABELS.getOrDefault(text, CC_MODE_LABELS.getOrDefault(text, text));
    }

    private String formatCondition(Map<String, Object> condition) {
        if (condition == null || condition.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(condition);
        } catch (Exception e) {
            return String.valueOf(condition);
        }
    }

    private String formatTimeout(Integer timeoutHours) {
        return timeoutHours == null ? "不限" : timeoutHours + " 小时";
    }

    private String nodeTypeLabel(String type) {
        return type == null ? null : NODE_TYPE_LABELS.getOrDefault(type, type);
    }

    private String timeoutActionLabel(String action) {
        return action == null ? null : TIMEOUT_ACTION_LABELS.getOrDefault(action, action);
    }

    private String displayName(String type, String name) {
        String typeLabel = nodeTypeLabel(type);
        return StringUtils.hasText(name) ? name : (typeLabel != null ? typeLabel + "节点" : "节点");
    }

    private String stripPrefix(String id, String prefix) {
        if (id == null) {
            return null;
        }
        return id.startsWith(prefix) ? id.substring(prefix.length()) : id;
    }
}
