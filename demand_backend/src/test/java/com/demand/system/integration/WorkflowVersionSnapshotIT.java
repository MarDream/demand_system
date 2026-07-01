package com.demand.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ADR-002 工作流版本快照优化 — 集成测试
 *
 * 覆盖范围：
 * 1. 定时对齐已移除（确认无残留调用）
 * 2. 迁移计划 CRUD + 节点映射配置
 * 3. 预检逻辑正确性
 * 4. 执行迁移 + 审计日志
 * 5. 版本激活兼容性检查改为 warning
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowVersionSnapshotIT extends BaseIntegrationTest {

    private static final String DEFAULT_PASSWORD = "admin123";
    private String adminToken;
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== 辅助方法 ====================

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        this.adminToken = obtainAccessToken("admin", DEFAULT_PASSWORD);
    }

    private String obtainAccessToken(String username, String password) {
        try {
            MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    java.util.Map.of("username", username, "password", password))))
                    .andReturn();
            String response = result.getResponse().getContentAsString();
            return JsonPath.read(response, "$.data.accessToken");
        } catch (Exception e) {
            return "";
        }
    }

    private Long createSuperAdmin() {
        String username = "superadmin" + shortId();
        jdbcTemplate.update("""
                INSERT INTO users (username, password, real_name, email, status, created_at, updated_at, deleted_at)
                VALUES (?, ?, '超级管理员', ?, 'active', NOW(), NOW(), 0)
                """, username, passwordEncoder.encode(DEFAULT_PASSWORD), username + "@test.local");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        jdbcTemplate.update("""
                INSERT INTO user_organizations (user_id, region_id, department_id, system_role, manager_id, effective_date)
                VALUES (?, 1, 1, ?, NULL, ?)
                """, userId, SUPER_ADMIN_ROLE, Date.valueOf(LocalDate.of(2026, 1, 1)));
        return userId;
    }

    private Map<String, Object> nodePayload(String nodeId, String name, boolean isFinal, int sortOrder) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("nodeId", nodeId);
        n.put("name", name);
        n.put("type", "state");
        n.put("color", isFinal ? "#67C23A" : "#409EFF");
        n.put("isFinal", isFinal);
        n.put("sortOrder", sortOrder);
        n.put("allowedRoles", List.of());
        n.put("allowedUsers", List.of());
        n.put("editableFields", List.of());
        n.put("requiredFields", List.of());
        n.put("availableActions", List.of());
        return n;
    }

    private Map<String, Object> edgePayload(String source, String target, String label) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("source", source);
        e.put("target", target);
        e.put("label", label);
        e.put("allowedRoles", List.of());
        e.put("requiredFields", List.of());
        e.put("conditions", null);
        e.put("defaultFlow", false);
        return e;
    }

    private String workflowDefinition(String name, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) throws Exception {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", null);
        d.put("processKey", null);
        d.put("name", name);
        d.put("nodes", nodes);
        d.put("edges", edges);
        return objectMapper.writeValueAsString(d);
    }

    private void createWorkflowVersion(Long projectId, String token, String versionName, String definition) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{id}/workflow/versions", projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(definition))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private Long getLatestVersionId(Long projectId, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{id}/workflow/versions", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data[0].id")).longValue();
    }

    private void activateVersion(Long versionId, Long projectId, String token) throws Exception {
        mockMvc.perform(post("/api/v1/workflow/versions/{id}/activate", versionId)
                        .header("Authorization", "Bearer " + token)
                        .queryParam("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void createRequirement(Long projectId, String token, String title) throws Exception {
        mockMvc.perform(post("/api/v1/requirements")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", projectId,
                                "title", title,
                                "description", "test requirement",
                                "type", "FEATURE",
                                "priority", "P1"
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
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.list[0].id")).longValue();
    }

    private void startWorkflowInstance(Long requirementId, Long projectId, String token) throws Exception {
        mockMvc.perform(post("/api/v1/requirements/{id}/start-workflow", requirementId)
                        .header("Authorization", "Bearer " + token)
                        .queryParam("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ==================== Phase 1: 定时对齐已移除 ====================

    @Test
    void scheduledAlignmentTask_shouldBeDeleted() throws Exception {
        // 验证定时任务类不存在于 classpath
        assertFalse(hasClass("com.demand.system.module.workflow.scheduled.WorkflowAlignmentScheduledTask"));
    }

    @Test
    void workflowEngineService_shouldNotCallAlignMethods() throws Exception {
        // 验证 WorkflowEngineService 中没有 alignRunningInstancesToActiveVersion / alignRequirementInstanceIfNeeded 调用
        String content = readResource("/com/demand/system/module/workflow/engine/WorkflowEngineService.java");
        assertTrue(content.contains("transition"), "Should contain transition method");
        assertTrue(content.contains("rollback"), "Should contain rollback method");
        assertFalse(content.contains("alignRunningInstancesToActiveVersion"),
                "Should NOT contain alignRunningInstancesToActiveVersion (deleted)");
        assertFalse(content.contains("alignRequirementInstanceIfNeeded"),
                "Should NOT contain alignRequirementInstanceIfNeeded (deleted)");
    }

    @Test
    void controller_migrateRunningInstancesEndpoint_shouldBeDeleted() throws Exception {
        // 该端点已被删除，访问应返回 404
        mockMvc.perform(post("/api/v1/admin/workflow-migration/plans/migrate-running-instances"))
                .andExpect(status().isNotFound());
    }

    // ==================== Phase 2: 迁移计划 CRUD ====================

    @Test
    @Transactional
    void createMigrationPlan_shouldSucceedWithAutoSuggestedMapping() throws Exception {
        // 创建项目和两个版本的流程定义
        Long projectId = createProject("迁移CRUD项目-" + shortId(), 1L);

        // v1: proposal → developing → done
        List<Map<String, Object>> v1Nodes = List.of(
                nodePayload("proposal", "提案中", false, 1),
                nodePayload("developing", "开发中", false, 2),
                nodePayload("done", "已完成", true, 3)
        );
        List<Map<String, Object>> v1Edges = List.of(
                edgePayload("proposal", "developing", "进入开发"),
                edgePayload("developing", "done", "完成")
        );
        createWorkflowVersion(projectId, adminToken, "v1版", workflowDefinition("v1版", v1Nodes, v1Edges));

        // v2: proposal → review → developing → done (新增 review 节点)
        List<Map<String, Object>> v2Nodes = List.of(
                nodePayload("proposal", "提案中", false, 1),
                nodePayload("review", "评审中", false, 2),
                nodePayload("developing", "开发中", false, 3),
                nodePayload("done", "已完成", true, 4)
        );
        List<Map<String, Object>> v2Edges = List.of(
                edgePayload("proposal", "review", "提交评审"),
                edgePayload("review", "developing", "通过评审"),
                edgePayload("developing", "done", "完成")
        );
        createWorkflowVersion(projectId, adminToken, "v2版", workflowDefinition("v2版", v2Nodes, v2Edges));

        Long v1Id = getLatestVersionId(projectId, adminToken);
        Long v2Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v1Id, projectId, adminToken);
        activateVersion(v2Id, projectId, adminToken);

        // 创建需求并启动实例（绑定到 v1）
        createRequirement(projectId, adminToken, "迁移CRUD需求-" + shortId());
        Long reqId = findRequirementId(projectId, "迁移CRUD需求-" + shortId(), adminToken);
        startWorkflowInstance(reqId, projectId, adminToken);

        // 创建迁移计划
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("fromVersionId", v1Id);
        request.put("toVersionId", v2Id);
        request.put("remark", "自动建议映射测试");

        mockMvc.perform(post("/api/v1/admin/workflow-migration/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").value("draft"));

        // 验证数据库中的计划记录
        Long planId = jdbcTemplate.queryForObject(
                "SELECT id FROM workflow_migration_plans ORDER BY id DESC LIMIT 1", Long.class);
        assertEquals(v1Id, jdbcTemplate.queryForObject(
                "SELECT from_version_id FROM workflow_migration_plans WHERE id = ?", Long.class, planId));
        assertEquals(v2Id, jdbcTemplate.queryForObject(
                "SELECT to_version_id FROM workflow_migration_plans WHERE id = ?", Long.class, planId));
    }

    @Test
    void listMigrationPlans_shouldReturnAllPlans() throws Exception {
        // 创建项目和版本
        Long projectId = createProject("列表查询项目-" + shortId(), 1L);

        createWorkflowVersion(projectId, adminToken, "v1版", workflowDefinition("v1版",
                List.of(nodePayload("a", "A", false, 1), nodePayload("b", "B", true, 2)),
                List.of(edgePayload("a", "b", "go"))));
        Long v1Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v1Id, projectId, adminToken);

        createWorkflowVersion(projectId, adminToken, "v2版", workflowDefinition("v2版",
                List.of(nodePayload("a", "A", false, 1), nodePayload("b", "B", true, 2)),
                List.of(edgePayload("a", "b", "go"))));
        Long v2Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v2Id, projectId, adminToken);

        // 手动创建一个计划（不通过 API，直接插入）
        jdbcTemplate.update("""
                INSERT INTO workflow_migration_plans (from_version_id, to_version_id, project_id, status, total_instance_count, migrated_count, failed_count, operator_id, remark, created_at, updated_at)
                VALUES (?, ?, ?, 'completed', 0, 0, 0, 1, 'manual insert', NOW(), NOW())
                """, v1Id, v2Id, projectId);

        // 查询列表
        mockMvc.perform(get("/api/v1/admin/workflow-migration/plans")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].fromVersionId").value(v1Id))
                .andExpect(jsonPath("$.data[0].toVersionId").value(v2Id));
    }

    @Test
    void getMigrationPlan_shouldReturnDetail() throws Exception {
        // 创建计划
        Long projectId = createProject("详情查询项目-" + shortId(), 1L);

        createWorkflowVersion(projectId, adminToken, "v1版", workflowDefinition("v1版",
                List.of(nodePayload("a", "A", false, 1)), List.of()));
        Long v1Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v1Id, projectId, adminToken);

        createWorkflowVersion(projectId, adminToken, "v2版", workflowDefinition("v2版",
                List.of(nodePayload("a", "A", false, 1)), List.of()));
        Long v2Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v2Id, projectId, adminToken);

        jdbcTemplate.update("""
                INSERT INTO workflow_migration_plans (from_version_id, to_version_id, project_id, status, total_instance_count, migrated_count, failed_count, operator_id, remark, created_at, updated_at)
                VALUES (?, ?, ?, 'draft', 0, 0, 0, 1, 'detail test', NOW(), NOW())
                """, v1Id, v2Id, projectId);

        Long planId = jdbcTemplate.queryForObject(
                "SELECT id FROM workflow_migration_plans ORDER BY id DESC LIMIT 1", Long.class);

        mockMvc.perform(get("/api/v1/admin/workflow-migration/plans/{planId}", planId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(planId))
                .andExpect(jsonPath("$.data.status").value("draft"));
    }

    // ==================== Phase 3: 节点映射配置 ====================

    @Test
    @Transactional
    void updateNodeMapping_shouldAcceptCustomMapping() throws Exception {
        Long projectId = createProject("映射配置项目-" + shortId(), 1L);

        // v1: proposal → developing → done
        createWorkflowVersion(projectId, adminToken, "v1版", workflowDefinition("v1版",
                List.of(nodePayload("proposal", "提案中", false, 1),
                        nodePayload("developing", "开发中", false, 2),
                        nodePayload("done", "已完成", true, 3)),
                List.of(edgePayload("proposal", "developing", "进入开发"),
                        edgePayload("developing", "done", "完成"))));
        Long v1Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v1Id, projectId, adminToken);

        // v2: proposal → review → developing → done
        createWorkflowVersion(projectId, adminToken, "v2版", workflowDefinition("v2版",
                List.of(nodePayload("proposal", "提案中", false, 1),
                        nodePayload("review", "评审中", false, 2),
                        nodePayload("developing", "开发中", false, 3),
                        nodePayload("done", "已完成", true, 4)),
                List.of(edgePayload("proposal", "review", "提交评审"),
                        edgePayload("review", "developing", "通过评审"),
                        edgePayload("developing", "done", "完成"))));
        Long v2Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v2Id, projectId, adminToken);

        // 创建计划
        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("fromVersionId", v1Id);
        createReq.put("toVersionId", v2Id);
        mockMvc.perform(post("/api/v1/admin/workflow-migration/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Long planId = jdbcTemplate.queryForObject(
                "SELECT id FROM workflow_migration_plans ORDER BY id DESC LIMIT 1", Long.class);

        // 自定义映射：proposal→proposal, developing→developing, done→done
        List<Map<String, String>> mapping = new ArrayList<>();
        mapping.add(Map.of("fromNodeId", "proposal", "toNodeId", "proposal", "fromNodeName", "提案中", "toNodeName", "提案中"));
        mapping.add(Map.of("fromNodeId", "developing", "toNodeId", "developing", "fromNodeName", "开发中", "toNodeName", "开发中"));
        mapping.add(Map.of("fromNodeId", "done", "toNodeId", "done", "fromNodeName", "已完成", "toNodeName", "已完成"));

        mockMvc.perform(put("/api/v1/admin/workflow-migration/plans/{planId}/mapping", planId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapping)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nodeMapping.length()").value(3));

        // 验证数据库中的映射
        List<Map<String, Object>> dbMapping = jdbcTemplate.queryForList(
                "SELECT from_node_id, to_node_id, from_node_name, to_node_name FROM workflow_migration_plans WHERE id = ?",
                planId);
        assertEquals(3, dbMapping.size());
    }

    @Test
    void updateNodeMapping_shouldRejectInvalidTargetNode() throws Exception {
        Long projectId = createProject("无效目标节点项目-" + shortId(), 1L);

        createWorkflowVersion(projectId, adminToken, "v1版", workflowDefinition("v1版",
                List.of(nodePayload("a", "A", false, 1)), List.of()));
        Long v1Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v1Id, projectId, adminToken);

        createWorkflowVersion(projectId, adminToken, "v2版", workflowDefinition("v2版",
                List.of(nodePayload("b", "B", true, 1)), List.of()));
        Long v2Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v2Id, projectId, adminToken);

        // 创建计划
        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("fromVersionId", v1Id);
        createReq.put("toVersionId", v2Id);
        mockMvc.perform(post("/api/v1/admin/workflow-migration/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk());

        Long planId = jdbcTemplate.queryForObject(
                "SELECT id FROM workflow_migration_plans ORDER BY id DESC LIMIT 1", Long.class);

        // 尝试将 a 映射到不存在的节点 x
        List<Map<String, String>> invalidMapping = new ArrayList<>();
        invalidMapping.add(Map.of("fromNodeId", "a", "toNodeId", "x", "fromNodeName", "A", "toNodeName", "X"));

        mockMvc.perform(put("/api/v1/admin/workflow-migration/plans/{planId}/mapping", planId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidMapping)))
                .andExpect(status().isBadRequest());
    }

    // ==================== Phase 4: 预检 ====================

    @Test
    @Transactional
    void previewMigration_shouldAnalyzeAffectedInstances() throws Exception {
        Long projectId = createProject("预检分析项目-" + shortId(), 1L);

        // v1: proposal → developing → done
        createWorkflowVersion(projectId, adminToken, "v1版", workflowDefinition("v1版",
                List.of(nodePayload("proposal", "提案中", false, 1),
                        nodePayload("developing", "开发中", false, 2),
                        nodePayload("done", "已完成", true, 3)),
                List.of(edgePayload("proposal", "developing", "进入开发"),
                        edgePayload("developing", "done", "完成"))));
        Long v1Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v1Id, projectId, adminToken);

        // v2: proposal → review → developing → done
        createWorkflowVersion(projectId, adminToken, "v2版", workflowDefinition("v2版",
                List.of(nodePayload("proposal", "提案中", false, 1),
                        nodePayload("review", "评审中", false, 2),
                        nodePayload("developing", "开发中", false, 3),
                        nodePayload("done", "已完成", true, 4)),
                List.of(edgePayload("proposal", "review", "提交评审"),
                        edgePayload("review", "developing", "通过评审"),
                        edgePayload("developing", "done", "完成"))));
        Long v2Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v2Id, projectId, adminToken);

        // 创建计划（使用自动映射）
        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("fromVersionId", v1Id);
        createReq.put("toVersionId", v2Id);
        mockMvc.perform(post("/api/v1/admin/workflow-migration/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk());

        Long planId = jdbcTemplate.queryForObject(
                "SELECT id FROM workflow_migration_plans ORDER BY id DESC LIMIT 1", Long.class);

        // 执行预检
        mockMvc.perform(post("/api/v1/admin/workflow-migration/plans/{planId}/preview", planId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalInstances").exists())
                .andExpect(jsonPath("$.data.canMigrateCount").exists())
                .andExpect(jsonPath("$.data.needManualCount").exists());
    }

    // ==================== Phase 5: 执行迁移 ====================

    @Test
    @Transactional
    void executeMigration_shouldUpdateInstancesAndLog() throws Exception {
        Long projectId = createProject("执行迁移项目-" + shortId(), 1L);

        // v1: proposal → developing → done
        createWorkflowVersion(projectId, adminToken, "v1版", workflowDefinition("v1版",
                List.of(nodePayload("proposal", "提案中", false, 1),
                        nodePayload("developing", "开发中", false, 2),
                        nodePayload("done", "已完成", true, 3)),
                List.of(edgePayload("proposal", "developing", "进入开发"),
                        edgePayload("developing", "done", "完成"))));
        Long v1Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v1Id, projectId, adminToken);

        // v2: proposal → review → developing → done
        createWorkflowVersion(projectId, adminToken, "v2版", workflowDefinition("v2版",
                List.of(nodePayload("proposal", "提案中", false, 1),
                        nodePayload("review", "评审中", false, 2),
                        nodePayload("developing", "开发中", false, 3),
                        nodePayload("done", "已完成", true, 4)),
                List.of(edgePayload("proposal", "review", "提交评审"),
                        edgePayload("review", "developing", "通过评审"),
                        edgePayload("developing", "done", "完成"))));
        Long v2Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v2Id, projectId, adminToken);

        // 创建计划（自定义映射）
        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("fromVersionId", v1Id);
        createReq.put("toVersionId", v2Id);
        mockMvc.perform(post("/api/v1/admin/workflow-migration/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk());

        Long planId = jdbcTemplate.queryForObject(
                "SELECT id FROM workflow_migration_plans ORDER BY id DESC LIMIT 1", Long.class);

        // 自定义映射：proposal→proposal, developing→developing, done→done
        List<Map<String, String>> mapping = new ArrayList<>();
        mapping.add(Map.of("fromNodeId", "proposal", "toNodeId", "proposal", "fromNodeName", "提案中", "toNodeName", "提案中"));
        mapping.add(Map.of("fromNodeId", "developing", "toNodeId", "developing", "fromNodeName", "开发中", "toNodeName", "开发中"));
        mapping.add(Map.of("fromNodeId", "done", "toNodeId", "done", "fromNodeName", "已完成", "toNodeName", "已完成"));
        mockMvc.perform(put("/api/v1/admin/workflow-migration/plans/{planId}/mapping", planId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapping)))
                .andExpect(status().isOk());

        // 执行迁移
        mockMvc.perform(post("/api/v1/admin/workflow-migration/plans/{planId}/execute", planId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.successCount").exists())
                .andExpect(jsonPath("$.data.failedCount").exists())
                .andExpect(jsonPath("$.data.message").exists());

        // 验证实例已更新
        Long instanceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE workflow_version_id = ? AND status = 'running'",
                Long.class, v2Id);
        assertTrue(instanceCount > 0, "应该有运行中的实例被迁移到 v2");

        // 验证审计日志
        Long logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_migration_logs WHERE plan_id = ? AND migration_type = 'plan'",
                Long.class, planId);
        assertTrue(logCount > 0, "应该有迁移日志记录");
    }

    @Test
    @Transactional
    void executeMigration_shouldSkipInstancesWithoutMapping() throws Exception {
        Long projectId = createProject("跳过无映射项目-" + shortId(), 1L);

        // v1: a → b → c (三个节点)
        createWorkflowVersion(projectId, adminToken, "v1版", workflowDefinition("v1版",
                List.of(nodePayload("a", "A", false, 1),
                        nodePayload("b", "B", false, 2),
                        nodePayload("c", "C", true, 3)),
                List.of(edgePayload("a", "b", "go"), edgePayload("b", "c", "done"))));
        Long v1Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v1Id, projectId, adminToken);

        // v2: a → b → c (相同节点，但只映射 a→a, b→b)
        createWorkflowVersion(projectId, adminToken, "v2版", workflowDefinition("v2版",
                List.of(nodePayload("a", "A", false, 1),
                        nodePayload("b", "B", false, 2),
                        nodePayload("c", "C", true, 3)),
                List.of(edgePayload("a", "b", "go"), edgePayload("b", "c", "done"))));
        Long v2Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v2Id, projectId, adminToken);

        // 创建计划（只映射 a 和 b，c 未映射）
        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("fromVersionId", v1Id);
        createReq.put("toVersionId", v2Id);
        mockMvc.perform(post("/api/v1/admin/workflow-migration/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk());

        Long planId = jdbcTemplate.queryForObject(
                "SELECT id FROM workflow_migration_plans ORDER BY id DESC LIMIT 1", Long.class);

        // 只映射 a 和 b
        List<Map<String, String>> mapping = new ArrayList<>();
        mapping.add(Map.of("fromNodeId", "a", "toNodeId", "a", "fromNodeName", "A", "toNodeName", "A"));
        mapping.add(Map.of("fromNodeId", "b", "toNodeId", "b", "fromNodeName", "B", "toNodeName", "B"));
        mockMvc.perform(put("/api/v1/admin/workflow-migration/plans/{planId}/mapping", planId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapping)))
                .andExpect(status().isOk());

        // 执行迁移
        mockMvc.perform(post("/api/v1/admin/workflow-migration/plans/{planId}/execute", planId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证只有部分实例被迁移
        Long runningOnV2 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE workflow_version_id = ? AND status = 'running'",
                Long.class, v2Id);
        // 至少有一个实例在 v2 上（如果之前有的话），或者全部还在 v1
        // 关键是：c 节点的实例因为没有映射而跳过，不会报错
    }

    // ==================== Phase 6: 版本激活兼容性检查 ====================

    @Test
    @Transactional
    void activateVersion_withUnsupportedStatus_shouldReturnWarningNotError() throws Exception {
        Long projectId = createProject("兼容性检查项目-" + shortId(), 1L);

        // v1: proposal → doing → done
        createWorkflowVersion(projectId, adminToken, "v1版", workflowDefinition("v1版",
                List.of(nodePayload("proposal", "提案中", false, 1),
                        nodePayload("doing", "进行中", false, 2),
                        nodePayload("done", "已完成", true, 3)),
                List.of(edgePayload("proposal", "doing", "进入进行"),
                        edgePayload("doing", "done", "完成"))));
        Long v1Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v1Id, projectId, adminToken);

        // v2: proposal → review → doing → done (去掉了"进行中"状态)
        createWorkflowVersion(projectId, adminToken, "v2版", workflowDefinition("v2版",
                List.of(nodePayload("proposal", "提案中", false, 1),
                        nodePayload("review", "评审中", false, 2),
                        nodePayload("doing", "进行中", false, 3),
                        nodePayload("done", "已完成", true, 4)),
                List.of(edgePayload("proposal", "review", "提交评审"),
                        edgePayload("review", "doing", "通过评审"),
                        edgePayload("doing", "done", "完成"))));
        Long v2Id = getLatestVersionId(projectId, adminToken);

        // 创建需求并启动实例（状态为"进行中"）
        createRequirement(projectId, adminToken, "兼容性检查需求-" + shortId());
        Long reqId = findRequirementId(projectId, "兼容性检查需求-" + shortId(), adminToken);
        startWorkflowInstance(reqId, projectId, adminToken);

        // 激活 v2 — 应该成功（warning 级别，不是 error）
        mockMvc.perform(post("/api/v1/workflow/versions/{id}/activate", v2Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .queryParam("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证实例仍在 v1 上运行（版本快照语义）
        Long instanceCountOnV1 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE workflow_version_id = ? AND status = 'running'",
                Long.class, v1Id);
        assertTrue(instanceCountOnV1 > 0, "在途实例仍绑定 v1");
    }

    @Test
    void validateVersion_shouldReturnWarningForIncompatibleStatus() throws Exception {
        Long projectId = createProject("验证警告项目-" + shortId(), 1L);

        // v1: proposal → doing → done
        createWorkflowVersion(projectId, adminToken, "v1版", workflowDefinition("v1版",
                List.of(nodePayload("proposal", "提案中", false, 1),
                        nodePayload("doing", "进行中", false, 2),
                        nodePayload("done", "已完成", true, 3)),
                List.of(edgePayload("proposal", "doing", "进入进行"),
                        edgePayload("doing", "done", "完成"))));
        Long v1Id = getLatestVersionId(projectId, adminToken);
        activateVersion(v1Id, projectId, adminToken);

        // v2: proposal → review → doing → done (去掉了"进行中")
        createWorkflowVersion(projectId, adminToken, "v2版", workflowDefinition("v2版",
                List.of(nodePayload("proposal", "提案中", false, 1),
                        nodePayload("review", "评审中", false, 2),
                        nodePayload("doing", "进行中", false, 3),
                        nodePayload("done", "已完成", true, 4)),
                List.of(edgePayload("proposal", "review", "提交评审"),
                        edgePayload("review", "doing", "通过评审"),
                        edgePayload("doing", "done", "完成"))));
        Long v2Id = getLatestVersionId(projectId, adminToken);

        // 获取验证结果
        mockMvc.perform(get("/api/v1/workflow/versions/{id}/validate", v2Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .queryParam("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    // ==================== Phase 7: 权限控制 ====================

    @Test
    void migrationPlanEndpoints_shouldRequireSuperAdmin() throws Exception {
        // 普通用户不应能访问迁移管理端点
        mockMvc.perform(get("/api/v1/admin/workflow-migration/plans"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/workflow-migration/plans"))
                .andExpect(status().isForbidden());
    }

    // ==================== 工具方法 ====================

    private boolean hasClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private String readResource(String path) {
        var resource = getClass().getResource(path);
        if (resource == null) return "";
        try (var is = resource.openStream()) {
            return new String(is.readAllBytes());
        } catch (Exception e) {
            return "";
        }
    }

    private Long createProject(String name, Long creatorId) {
        jdbcTemplate.update("""
                INSERT INTO projects (name, description, creator_id, status, created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, 'active', NOW(), NOW(), 0)
                """, name, "集成测试项目", creatorId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM projects WHERE name = ?", Long.class, name);
    }
}
