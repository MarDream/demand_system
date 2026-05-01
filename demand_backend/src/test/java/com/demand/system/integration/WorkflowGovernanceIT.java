package com.demand.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class WorkflowGovernanceIT extends BaseIntegrationTest {

    private static final String DEFAULT_PASSWORD = "admin123";
    private static final String DEFAULT_PASSWORD_HASH = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void workflowConfigEndpoints_shouldAllowConfigRoleAndRejectPlainUser() throws Exception {
        Long projectId = createProject("配置权限项目-" + UUID.randomUUID(), 1L);
        String configUsername = "wf-config-" + shortId();
        String plainUsername = "wf-user-" + shortId();
        createUser(configUsername, "流程配置员", "workflow:config");
        createUser(plainUsername, "普通成员", "USER");

        String configToken = loginAndGetAccessToken(configUsername, DEFAULT_PASSWORD);
        String plainToken = loginAndGetAccessToken(plainUsername, DEFAULT_PASSWORD);

        String definition = workflowDefinition(
                "配置权限流",
                List.of(
                        node("draft", "草稿", false, 1, List.of(), List.of()),
                        node("done", "已完成", true, 2, List.of(), List.of())
                ),
                List.of(edge("draft", "done", "完成", List.of(), List.of(), ""))
        );

        mockMvc.perform(post("/api/v1/projects/{id}/workflow/versions", projectId)
                        .header("Authorization", "Bearer " + configToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionPayload("配置权限版", definition)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/v1/projects/{id}/workflow/versions", projectId)
                        .header("Authorization", "Bearer " + plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionPayload("无权限版", definition)))
                .andExpect(status().isForbidden());
    }

    @Test
    void activateVersion_shouldPublishRuntimeAndApplyInitialStateOnRequirementCreation() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", DEFAULT_PASSWORD);
        Long projectId = createProject("发布验证项目-" + UUID.randomUUID(), 1L);

        String definition = workflowDefinition(
                "发布验证流",
                List.of(
                        node("proposal", "提案中", false, 1, List.of(), List.of()),
                        node("developing", "开发中", false, 2, List.of(), List.of()),
                        node("done", "已完成", true, 3, List.of(), List.of())
                ),
                List.of(
                        edge("proposal", "developing", "进入开发", List.of(), List.of(), ""),
                        edge("developing", "done", "完成", List.of(), List.of(), "")
                )
        );

        createWorkflowVersion(projectId, adminToken, "发布版", definition);
        Long versionId = getLatestWorkflowVersionId(projectId, adminToken);
        activateWorkflowVersion(versionId, projectId, adminToken);

        mockMvc.perform(get("/api/v1/projects/{id}/workflow/states", projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("提案中"))
                .andExpect(jsonPath("$.data[1].name").value("开发中"))
                .andExpect(jsonPath("$.data[2].name").value("已完成"));

        mockMvc.perform(get("/api/v1/projects/{id}/workflow/transitions", projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2));

        String title = "初始状态需求-" + UUID.randomUUID();
        createRequirement(projectId, adminToken, title);
        Long requirementId = findRequirementId(projectId, title, adminToken);

        mockMvc.perform(get("/api/v1/requirements/{id}", requirementId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("提案中"));
    }

    @Test
    void availableTransitions_shouldRespectRoleAndSpecificUserNodePermissions() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", DEFAULT_PASSWORD);
        String suffix = shortId();
        String productUsername = "pm-" + suffix;
        String designatedUsername = "assignee-" + suffix;
        String outsiderUsername = "outsider-" + suffix;

        createUser(productUsername, "产品流转人", "产品经理");
        Long designatedUserId = createUser(designatedUsername, "指定处理人", "USER");
        createUser(outsiderUsername, "旁观者", "USER");

        String productToken = loginAndGetAccessToken(productUsername, DEFAULT_PASSWORD);
        String designatedToken = loginAndGetAccessToken(designatedUsername, DEFAULT_PASSWORD);
        String outsiderToken = loginAndGetAccessToken(outsiderUsername, DEFAULT_PASSWORD);

        Long projectId = createProject("权限流项目-" + UUID.randomUUID(), 1L);
        String definition = workflowDefinition(
                "节点权限流",
                List.of(
                        node("dispatch", "待分派", false, 1, List.of("产品经理"), List.of()),
                        node("processing", "处理中", false, 2, List.of(), List.of(designatedUserId)),
                        node("done", "已完成", true, 3, List.of(), List.of())
                ),
                List.of(
                        edge("dispatch", "processing", "指派", List.of(), List.of(), ""),
                        edge("processing", "done", "完成", List.of(), List.of(), "")
                )
        );
        createWorkflowVersion(projectId, adminToken, "权限版", definition);
        Long versionId = getLatestWorkflowVersionId(projectId, adminToken);
        activateWorkflowVersion(versionId, projectId, adminToken);

        String title = "权限验证需求-" + UUID.randomUUID();
        createRequirement(projectId, adminToken, title);
        Long requirementId = findRequirementId(projectId, title, adminToken);

        mockMvc.perform(get("/api/v1/requirements/{id}/available-transitions", requirementId)
                        .header("Authorization", "Bearer " + productToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/v1/requirements/{id}/available-transitions", requirementId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));

        Long processingStateId = getStateId(projectId, "处理中");
        mockMvc.perform(post("/api/v1/requirements/{id}/transition", requirementId)
                        .header("Authorization", "Bearer " + productToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionPayload(processingStateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.newStatus").value("处理中"));

        mockMvc.perform(get("/api/v1/requirements/{id}/available-transitions", requirementId)
                        .header("Authorization", "Bearer " + designatedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/v1/requirements/{id}/available-transitions", requirementId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));

        Long doneStateId = getStateId(projectId, "已完成");
        mockMvc.perform(post("/api/v1/requirements/{id}/transition", requirementId)
                        .header("Authorization", "Bearer " + designatedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionPayload(doneStateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.newStatus").value("已完成"));
    }

    @Test
    void requirementStatusAndDelete_shouldHonorWorkflowGovernanceRules() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", DEFAULT_PASSWORD);
        Long projectId = createProject("治理规则项目-" + UUID.randomUUID(), 1L);

        String definition = workflowDefinition(
                "治理规则流",
                List.of(
                        node("triage", "待评估", false, 1, List.of(), List.of()),
                        node("done", "已完成", true, 2, List.of(), List.of())
                ),
                List.of(edge("triage", "done", "完成", List.of(), List.of(), ""))
        );
        createWorkflowVersion(projectId, adminToken, "治理版", definition);
        Long versionId = getLatestWorkflowVersionId(projectId, adminToken);
        activateWorkflowVersion(versionId, projectId, adminToken);

        String title = "治理需求-" + UUID.randomUUID();
        createRequirement(projectId, adminToken, title);
        Long requirementId = findRequirementId(projectId, title, adminToken);

        mockMvc.perform(put("/api/v1/requirements/{id}", requirementId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusPayload(requirementId, "已完成")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("状态流转请使用工作流操作"));

        Long doneStateId = getStateId(projectId, "已完成");
        mockMvc.perform(post("/api/v1/requirements/{id}/transition", requirementId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionPayload(doneStateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(delete("/api/v1/requirements/{id}", requirementId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("已流转的需求不能删除"));
    }

    @Test
    void bpmnStyleDefinition_shouldAutoAdvanceToVisibleState() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", DEFAULT_PASSWORD);
        Long projectId = createProject("BPMN流程项目-" + UUID.randomUUID(), 1L);

        String definition = workflowDefinition(
                "优先级分流",
                "priority-routing",
                List.of(
                        typedNode("start", "开始", "startEvent", false, 0, List.of(), List.of()),
                        typedNode("normalize", "准备上下文", "serviceTask", false, 1, List.of(), List.of()),
                        typedNode("priority", "优先级判断", "exclusiveGateway", false, 2, List.of(), List.of()),
                        typedNode("fast-track", "快速处理", "userTask", false, 3, List.of(), List.of()),
                        typedNode("backlog", "需求池", "userTask", false, 4, List.of(), List.of()),
                        typedNode("done", "已完成", "endEvent", true, 5, List.of(), List.of())
                ),
                List.of(
                        edge("start", "normalize", "", List.of(), List.of(), ""),
                        edge("normalize", "priority", "", List.of(), List.of(), ""),
                        edge("priority", "fast-track", "高优先", List.of(), List.of(), condition("priority", "IN", List.of("P0", "P1"))),
                        edge("priority", "backlog", "默认进入需求池", List.of(), List.of(), "", true),
                        edge("fast-track", "done", "完成", List.of(), List.of(), ""),
                        edge("backlog", "done", "完成", List.of(), List.of(), "")
                )
        );

        createWorkflowVersion(projectId, adminToken, "BPMN版", definition);
        Long versionId = getLatestWorkflowVersionId(projectId, adminToken);
        activateWorkflowVersion(versionId, projectId, adminToken);

        mockMvc.perform(get("/api/v1/projects/{id}/workflow/states", projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("快速处理"))
                .andExpect(jsonPath("$.data[1].name").value("需求池"))
                .andExpect(jsonPath("$.data[2].name").value("已完成"));

        String fastTitle = "高优需求-" + UUID.randomUUID();
        createRequirement(projectId, adminToken, fastTitle, "P1");
        Long fastRequirementId = findRequirementId(projectId, fastTitle, adminToken);
        mockMvc.perform(get("/api/v1/requirements/{id}", fastRequirementId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("快速处理"));

        String normalTitle = "普通需求-" + UUID.randomUUID();
        createRequirement(projectId, adminToken, normalTitle, "P3");
        Long normalRequirementId = findRequirementId(projectId, normalTitle, adminToken);
        mockMvc.perform(get("/api/v1/requirements/{id}", normalRequirementId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("需求池"));
    }

    private String loginAndGetAccessToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private Long createUser(String username, String realName, String systemRole) {
        jdbcTemplate.update("""
                        INSERT INTO users (username, password, real_name, email, phone, avatar, status, created_at, updated_at, deleted_at)
                        VALUES (?, ?, ?, ?, NULL, NULL, 'active', NOW(), NOW(), 0)
                        """,
                username,
                DEFAULT_PASSWORD_HASH,
                realName,
                username + "@test.local"
        );

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?",
                Long.class,
                username
        );

        jdbcTemplate.update("""
                        INSERT INTO user_organizations (user_id, region_id, department_id, position_id, system_role, manager_id, effective_date)
                        VALUES (?, 1, 1, 1, ?, NULL, ?)
                        """,
                userId,
                systemRole,
                Date.valueOf(LocalDate.of(2026, 1, 1))
        );
        return userId;
    }

    private Long createProject(String projectName, Long creatorId) {
        jdbcTemplate.update("""
                        INSERT INTO projects (name, description, creator_id, status, created_at, updated_at, deleted_at)
                        VALUES (?, ?, ?, 'active', NOW(), NOW(), 0)
                        """,
                projectName,
                "集成测试项目",
                creatorId
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM projects WHERE name = ?",
                Long.class,
                projectName
        );
    }

    private void createWorkflowVersion(Long projectId, String token, String versionName, String definition) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{id}/workflow/versions", projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionPayload(versionName, definition)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private Long getLatestWorkflowVersionId(Long projectId, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{id}/workflow/versions", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data[0].id");
    }

    private void activateWorkflowVersion(Long versionId, Long projectId, String token) throws Exception {
        mockMvc.perform(post("/api/v1/workflow/versions/{id}/activate", versionId)
                        .header("Authorization", "Bearer " + token)
                        .queryParam("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void createRequirement(Long projectId, String token, String title) throws Exception {
        createRequirement(projectId, token, title, "P1");
    }

    private void createRequirement(Long projectId, String token, String title, String priority) throws Exception {
        mockMvc.perform(post("/api/v1/requirements")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", projectId,
                                "title", title,
                                "description", "workflow 集成测试需求",
                                "type", "FEATURE",
                                "priority", priority
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private Long findRequirementId(Long projectId, String title, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/requirements")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("projectId", String.valueOf(projectId))
                        .queryParam("keyword", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].title").value(title))
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.list[0].id");
    }

    private Long getStateId(Long projectId, String stateName) {
        Long stateId = jdbcTemplate.queryForObject(
                "SELECT id FROM workflow_states WHERE project_id = ? AND name = ?",
                Long.class,
                projectId,
                stateName
        );
        assertTrue(stateId != null && stateId > 0);
        return stateId;
    }

    private String versionPayload(String versionName, String definition) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", versionName,
                "definition", definition
        ));
    }

    private String transitionPayload(Long targetStateId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("targetStateId", targetStateId));
    }

    private String updateStatusPayload(Long requirementId, String status) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "id", requirementId,
                "status", status
        ));
    }

    private String workflowDefinition(String name, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) throws Exception {
        return workflowDefinition(name, null, nodes, edges);
    }

    private String workflowDefinition(String name,
                                      String processKey,
                                      List<Map<String, Object>> nodes,
                                      List<Map<String, Object>> edges) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", null);
        payload.put("processKey", processKey);
        payload.put("name", name);
        payload.put("nodes", nodes);
        payload.put("edges", edges);
        return objectMapper.writeValueAsString(payload);
    }

    private Map<String, Object> node(String nodeId,
                                     String name,
                                     boolean isFinal,
                                     int sortOrder,
                                     List<String> allowedRoles,
                                     List<Long> allowedUsers) {
        return typedNode(nodeId, name, "state", isFinal, sortOrder, allowedRoles, allowedUsers);
    }

    private Map<String, Object> typedNode(String nodeId,
                                          String name,
                                          String type,
                                          boolean isFinal,
                                          int sortOrder,
                                          List<String> allowedRoles,
                                          List<Long> allowedUsers) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("name", name);
        payload.put("type", type);
        payload.put("color", isFinal ? "#67C23A" : "#409EFF");
        payload.put("isFinal", isFinal);
        payload.put("sortOrder", sortOrder);
        payload.put("allowedRoles", allowedRoles);
        payload.put("allowedUsers", allowedUsers);
        payload.put("editableFields", List.of());
        payload.put("requiredFields", List.of());
        payload.put("availableActions", List.of());
        return payload;
    }

    private Map<String, Object> edge(String source,
                                     String target,
                                     String label,
                                     List<String> allowedRoles,
                                     List<String> requiredFields,
                                     String conditions) {
        return edge(source, target, label, allowedRoles, requiredFields, conditions, false);
    }

    private Map<String, Object> edge(String source,
                                     String target,
                                     String label,
                                     List<String> allowedRoles,
                                     List<String> requiredFields,
                                     String conditions,
                                     boolean defaultFlow) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", source);
        payload.put("target", target);
        payload.put("label", label);
        payload.put("allowedRoles", allowedRoles);
        payload.put("requiredFields", requiredFields);
        payload.put("conditions", conditions);
        payload.put("defaultFlow", defaultFlow);
        return payload;
    }

    private String condition(String field, String operator, Object value) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "field", field,
                "operator", operator,
                "value", value
        ));
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
