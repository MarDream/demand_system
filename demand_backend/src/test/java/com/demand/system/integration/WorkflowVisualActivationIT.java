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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 可视化工作流：审核 → 手动启用 → 草稿提交 → 实例流转。
 */
public class WorkflowVisualActivationIT extends BaseIntegrationTest {

    private static final String DEFAULT_PASSWORD = "admin123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void activateVisualWorkflow_shouldFailWithoutApproval() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", DEFAULT_PASSWORD);
        Long projectId = createProject("可视化未审核项目-" + UUID.randomUUID(), 1L);
        Long adminUserId = getAdminUserId();

        Long versionId = saveAndPublishVisualWorkflow(projectId, adminToken, adminUserId);

        mockMvc.perform(put("/api/v1/workflows/versions/{versionId}/activation", versionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void visualWorkflow_draftSubmitTransitionAndLockVersion_shouldWork() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", DEFAULT_PASSWORD);
        Long projectId = createProject("可视化全链路项目-" + UUID.randomUUID(), 1L);
        Long adminUserId = getAdminUserId();

        Long versionId = saveAndPublishVisualWorkflow(projectId, adminToken, adminUserId);
        Long approvalId = getPendingApprovalId(versionId);
        approveWorkflow(approvalId, adminToken);
        activateVisualWorkflow(versionId, adminToken);

        String title = "可视化草稿-" + UUID.randomUUID();
        Long requirementId = createDraftRequirement(projectId, adminToken, title);
        String nextNodeId = getFirstNextNodeId(requirementId, adminToken);
        submitDraft(requirementId, adminToken, nextNodeId);

        mockMvc.perform(get("/api/v1/requirements/{id}", requirementId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isDraft").value(false))
                .andExpect(jsonPath("$.data.workflowInstanceId").isNotEmpty())
                .andExpect(jsonPath("$.data.nodeStatus").value("PENDING_ANALYSIS"));

        Long transitionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_instance_transitions WHERE requirement_id = ?",
                Long.class,
                requirementId
        );
        assertEquals(1L, transitionCount);

        Integer lockVersion = jdbcTemplate.queryForObject(
                "SELECT lock_version FROM workflow_instances WHERE requirement_id = ?",
                Integer.class,
                requirementId
        );
        assertEquals(0, lockVersion);

        mockMvc.perform(post("/api/v1/workflow-engine/transition")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requirementId", requirementId,
                                "toNodeId", "node-end",
                                "action", "approve",
                                "rating", 5,
                                "lockVersion", 0
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/v1/workflow-engine/transition")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requirementId", requirementId,
                                "toNodeId", "node-end",
                                "action", "approve",
                                "rating", 5,
                                "lockVersion", 0
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void myPending_shouldMatchLegacyRoleNameAndApproveWorkflow() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", DEFAULT_PASSWORD);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String roleName = "运营需求分析员";
        String roleCode = "OPS_ANALYST_" + suffix;
        String analystUsername = "ops-analyst-" + suffix.toLowerCase();

        Long roleId = createRole(roleCode, roleName);
        Long analystUserId = createLegacyRoleUser(analystUsername, "运营分析员", roleName);
        String analystToken = loginAndGetAccessToken(analystUsername, DEFAULT_PASSWORD);

        Long projectId = createProject("角色待办项目-" + UUID.randomUUID(), 1L);
        Long versionId = saveAndPublishRoleBasedVisualWorkflow(projectId, adminToken, roleId);
        Long approvalId = getPendingApprovalId(versionId);
        approveWorkflow(approvalId, adminToken);
        activateVisualWorkflow(versionId, adminToken);

        Long firstRequirementId = submitDraftToFirstNode(projectId, adminToken, "角色待办-1-" + UUID.randomUUID());
        Long secondRequirementId = submitDraftToFirstNode(projectId, adminToken, "角色待办-2-" + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/requirements/my-pending")
                        .header("Authorization", "Bearer " + analystToken)
                        .queryParam("pageNum", "1")
                        .queryParam("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.list[0].status").value("待分析"));

        mockMvc.perform(post("/api/v1/workflow-engine/transition")
                        .header("Authorization", "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requirementId", firstRequirementId,
                                "toNodeId", "node-end",
                                "action", "approve",
                                "rating", 5,
                                "comment", "分析完成",
                                "lockVersion", 0
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/requirements/{id}", firstRequirementId)
                        .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("已验收"));

        mockMvc.perform(get("/api/v1/requirements/my-pending")
                        .header("Authorization", "Bearer " + analystToken)
                        .queryParam("pageNum", "1")
                        .queryParam("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(secondRequirementId));
    }

    @Test
    void workflowMigration_markLegacy_shouldTagRequirementsWithoutInstance() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", DEFAULT_PASSWORD);
        Long projectId = createProject("迁移标记项目-" + UUID.randomUUID(), 1L);
        String title = "历史需求-" + UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO requirements (project_id, title, description, type, priority, status, creator_id,
                                                  is_draft, legacy_workflow, deleted_at, version, order_num, created_at, updated_at)
                        VALUES (?, ?, 'legacy', 'FEATURE', 'P1', '新建', 1, 0, 0, 0, 0, 0, NOW(), NOW())
                        """,
                projectId, title);

        mockMvc.perform(post("/api/v1/admin/workflow-migration/mark-legacy")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.markedLegacyCount").value(1));

        Integer legacyFlag = jdbcTemplate.queryForObject(
                "SELECT legacy_workflow FROM requirements WHERE title = ?",
                Integer.class,
                title
        );
        assertEquals(1, legacyFlag);
    }

    private Long saveAndPublishVisualWorkflow(Long projectId, String token, Long adminUserId) throws Exception {
        Map<String, Object> config = visualWorkflowConfig(adminUserId);
        mockMvc.perform(post("/api/v1/workflows/{projectId}/config", projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MvcResult versionResult = mockMvc.perform(get("/api/v1/workflows/{projectId}/versions", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        Long versionId = JsonPath.read(versionResult.getResponse().getContentAsString(), "$.data[0].id");

        mockMvc.perform(post("/api/v1/workflows/{projectId}/publish", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        return versionId;
    }

    private void approveWorkflow(Long approvalId, String token) throws Exception {
        mockMvc.perform(post("/api/v1/workflow-approvals/{id}/approve", approvalId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"IT通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void activateVisualWorkflow(Long versionId, String token) throws Exception {
        mockMvc.perform(put("/api/v1/workflows/versions/{versionId}/activation", versionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isActive").value(1));
    }

    private Long getPendingApprovalId(Long versionId) {
        Long approvalId = jdbcTemplate.queryForObject("""
                        SELECT id FROM workflow_approvals
                        WHERE workflow_version_id = ? AND status = 'pending'
                        ORDER BY id DESC LIMIT 1
                        """,
                Long.class,
                versionId);
        assertTrue(approvalId != null && approvalId > 0);
        return approvalId;
    }

    private Long createDraftRequirement(Long projectId, String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/requirements/drafts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", projectId,
                                "title", title,
                                "description", "可视化集成测试",
                                "priority", "P1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data");
    }

    private Long submitDraftToFirstNode(Long projectId, String token, String title) throws Exception {
        Long requirementId = createDraftRequirement(projectId, token, title);
        String nextNodeId = getFirstNextNodeId(requirementId, token);
        submitDraft(requirementId, token, nextNodeId);
        return requirementId;
    }

    private String getFirstNextNodeId(Long requirementId, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/requirements/{id}/next-nodes", requirementId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].nodeId").exists())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data[0].nodeId");
    }

    private void submitDraft(Long requirementId, String token, String nextNodeId) throws Exception {
        mockMvc.perform(post("/api/v1/requirements/{id}/submit", requirementId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "version", 0,
                                "nextNodeId", nextNodeId,
                                "comment", "提交审批"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private Map<String, Object> visualWorkflowConfig(Long adminUserId) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("nodes", List.of(
                visualNode("node-start", "start", "开始", 100, 100, null, null, null),
                visualNode("node-approve", "approval", "待分析", 300, 100, "SPECIFIED_USER", List.of(adminUserId),
                        Map.of("nodeStatusCode", "PENDING_ANALYSIS")),
                visualNode("node-end", "end", "已验收", 500, 100, null, null, Map.of("nodeStatusCode", "ACCEPTED"))
        ));
        config.put("edges", List.of(
                visualEdge("edge-1", "node-start", "node-approve", "提交"),
                visualEdge("edge-2", "node-approve", "node-end", "完成")
        ));
        return config;
    }

    private Long saveAndPublishRoleBasedVisualWorkflow(Long projectId, String token, Long roleId) throws Exception {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("nodes", List.of(
                visualNode("node-start", "start", "开始", 100, 100, null, null, null),
                visualRoleNode("node-approve", "approval", "待分析", 300, 100, roleId.intValue(),
                        Map.of("nodeStatusCode", "PENDING_ANALYSIS")),
                visualNode("node-end", "end", "已验收", 500, 100, null, null, Map.of("nodeStatusCode", "ACCEPTED"))
        ));
        config.put("edges", List.of(
                visualEdge("edge-1", "node-start", "node-approve", "提交"),
                visualEdge("edge-2", "node-approve", "node-end", "完成")
        ));

        mockMvc.perform(post("/api/v1/workflows/{projectId}/config", projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MvcResult versionResult = mockMvc.perform(get("/api/v1/workflows/{projectId}/versions", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        Long versionId = JsonPath.read(versionResult.getResponse().getContentAsString(), "$.data[0].id");

        mockMvc.perform(post("/api/v1/workflows/{projectId}/publish", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        return versionId;
    }

    private Map<String, Object> visualNode(String nodeId,
                                           String nodeType,
                                           String nodeName,
                                           int x,
                                           int y,
                                           String assigneeType,
                                           List<Long> assigneeUserIds,
                                           Map<String, Object> properties) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeId", nodeId);
        node.put("nodeType", nodeType);
        node.put("nodeName", nodeName);
        node.put("positionX", x);
        node.put("positionY", y);
        if (assigneeType != null) {
            node.put("assigneeType", assigneeType);
            node.put("assigneeUserIds", assigneeUserIds);
        }
        if (properties != null) {
            node.put("properties", properties);
        }
        return node;
    }

    private Map<String, Object> visualRoleNode(String nodeId,
                                               String nodeType,
                                               String nodeName,
                                               int x,
                                               int y,
                                               Integer assigneeRoleId,
                                               Map<String, Object> properties) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeId", nodeId);
        node.put("nodeType", nodeType);
        node.put("nodeName", nodeName);
        node.put("positionX", x);
        node.put("positionY", y);
        node.put("assigneeType", "SPECIFIED_ROLE");
        node.put("assigneeRoleId", assigneeRoleId);
        if (properties != null) {
            node.put("properties", properties);
        }
        return node;
    }

    private Map<String, Object> visualEdge(String edgeId, String source, String target, String label) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("edgeId", edgeId);
        edge.put("sourceNodeId", source);
        edge.put("targetNodeId", target);
        edge.put("label", label);
        return edge;
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

    private Long getAdminUserId() {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'admin'", Long.class);
    }

    private Long createRole(String code, String name) {
        jdbcTemplate.update("""
                        INSERT INTO roles (code, name, description, is_system, deleted_at, created_at, updated_at)
                        VALUES (?, ?, ?, 0, 0, NOW(), NOW())
                        """,
                code, name, "集成测试角色");
        return jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, code);
    }

    private Long createLegacyRoleUser(String username, String realName, String systemRole) {
        jdbcTemplate.update("""
                        INSERT INTO users (username, password, real_name, email, phone, avatar, status, created_at, updated_at, deleted_at)
                        VALUES (?, ?, ?, ?, NULL, NULL, 'active', NOW(), NOW(), 0)
                        """,
                username,
                "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi",
                realName,
                username + "@test.local"
        );

        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        jdbcTemplate.update("""
                        INSERT INTO user_organizations (user_id, region_id, department_id, system_role, manager_id, effective_date)
                        VALUES (?, 1, 1, ?, NULL, ?)
                        """,
                userId,
                systemRole,
                Date.valueOf(LocalDate.of(2026, 1, 1)));
        return userId;
    }

    private Long createProject(String projectName, Long creatorId) {
        jdbcTemplate.update("""
                        INSERT INTO projects (name, description, creator_id, status, created_at, updated_at, deleted_at)
                        VALUES (?, ?, ?, 'active', NOW(), NOW(), 0)
                        """,
                projectName, "集成测试项目", creatorId);
        return jdbcTemplate.queryForObject("SELECT id FROM projects WHERE name = ?", Long.class, projectName);
    }
}
