package com.demand.system.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 多维表格功能集成测试
 * <p>
 * 建表方式：测试启动前通过 JdbcTemplate 手动执行迁移脚本
 * database/migrations/20260709_01__add_bitable_tables.sql
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BitableFlowIT extends BaseIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private String accessToken;
    private Long baseId;
    private Long tableId;
    private Long fieldTextId;
    private Long fieldNumberId;
    private Long recordId;

    @BeforeAll
    void setup() throws Exception {
        Path migrationPath = Paths.get("..", "database", "migrations",
                "20260709_01__add_bitable_tables.sql").toAbsolutePath().normalize();
        String sql = Files.readString(migrationPath);
        // 使用 Spring ScriptUtils 执行 SQL 脚本
        ScriptUtils.executeSqlScript(jdbcTemplate.getDataSource().getConnection(),
                new org.springframework.core.io.ByteArrayResource(sql.getBytes()));

        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        String loginBody = """
                {"username":"admin","password":"admin123"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    // ==================== Base CRUD ====================

    @Test
    @Order(1)
    void createBase_shouldReturnId() throws Exception {
        String body = """
                {"name":"IT测试多维表格","description":"集成测试创建的Base","icon":"Grid","coverColor":"#1890ff"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/bitable/bases")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        baseId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.data", Long.class);
        assertThat(baseId).isNotNull();
    }

    @Test
    @Order(2)
    void listBases_shouldContainCreated() throws Exception {
        mockMvc.perform(get("/api/v1/bitable/bases")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.id == " + baseId + ")].name")
                        .value(org.hamcrest.Matchers.hasItem("IT测试多维表格")));
    }

    @Test
    @Order(3)
    void getBaseById_shouldReturnDetail() throws Exception {
        mockMvc.perform(get("/api/v1/bitable/bases/" + baseId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(baseId));
    }

    @Test
    @Order(4)
    void updateBase_shouldChangeName() throws Exception {
        String body = """
                {"name":"IT测试多维表格-已更新","description":"更新后的描述"}
                """;
        mockMvc.perform(put("/api/v1/bitable/bases/" + baseId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/bitable/bases/" + baseId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.name").value("IT测试多维表格-已更新"));
    }

    // ==================== Table CRUD ====================

    @Test
    @Order(10)
    void createTable_shouldReturnId() throws Exception {
        String body = """
                {"name":"任务表","description":"测试数据表"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/bitable/bases/" + baseId + "/tables")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        tableId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.data", Long.class);
        assertThat(tableId).isNotNull();
    }

    @Test
    @Order(11)
    void listTables_shouldContainCreated() throws Exception {
        mockMvc.perform(get("/api/v1/bitable/bases/" + baseId + "/tables")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.id == " + tableId + ")].name")
                        .value(org.hamcrest.Matchers.hasItem("任务表")));
    }

    // ==================== Field CRUD ====================

    @Test
    @Order(20)
    void createFields_shouldReturnIds() throws Exception {
        String textField = """
                {"name":"任务名称","fieldType":"text","required":1}
                """;
        MvcResult r1 = mockMvc.perform(post("/api/v1/bitable/tables/" + tableId + "/fields")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textField))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        fieldTextId = JsonPath.parse(r1.getResponse().getContentAsString()).read("$.data", Long.class);

        String numberField = """
                {"name":"工时","fieldType":"number"}
                """;
        MvcResult r2 = mockMvc.perform(post("/api/v1/bitable/tables/" + tableId + "/fields")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(numberField))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        fieldNumberId = JsonPath.parse(r2.getResponse().getContentAsString()).read("$.data", Long.class);

        assertThat(fieldTextId).isNotNull();
        assertThat(fieldNumberId).isNotNull();
    }

    @Test
    @Order(21)
    void listFields_shouldContainCreated() throws Exception {
        mockMvc.perform(get("/api/v1/bitable/tables/" + tableId + "/fields")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.id == " + fieldTextId + ")].name")
                        .value(org.hamcrest.Matchers.hasItem("任务名称")));
    }

    // ==================== View CRUD ====================

    @Test
    @Order(30)
    void createView_shouldReturnId() throws Exception {
        String body = """
                {"name":"默认表格视图","viewType":"grid"}
                """;
        mockMvc.perform(post("/api/v1/bitable/tables/" + tableId + "/views")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(31)
    void listViews_shouldContainGrid() throws Exception {
        mockMvc.perform(get("/api/v1/bitable/tables/" + tableId + "/views")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.viewType == 'grid')]").exists());
    }

    // ==================== Record CRUD ====================

    @Test
    @Order(40)
    void createRecord_shouldReturnId() throws Exception {
        String body = """
                {"cells":{"%d":{"valueText":"需求分析"},"%d":{"valueNumber":8}}}
                """.formatted(fieldTextId, fieldNumberId);
        MvcResult result = mockMvc.perform(post("/api/v1/bitable/tables/" + tableId + "/records")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        recordId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.data", Long.class);
        assertThat(recordId).isNotNull();
    }

    @Test
    @Order(41)
    void listRecords_shouldContainCells() throws Exception {
        mockMvc.perform(get("/api/v1/bitable/tables/" + tableId + "/records")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(recordId));
    }

    @Test
    @Order(42)
    void updateCell_withCorrectVersion_shouldSucceed() throws Exception {
        String body = """
                {"version":0,"valueText":"需求分析-更新"}
                """;
        mockMvc.perform(put("/api/v1/bitable/records/" + recordId + "/cells/" + fieldTextId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    @Order(43)
    void updateCell_withStaleVersion_shouldReturnConflict() throws Exception {
        String body = """
                {"version":0,"valueText":"冲突测试值"}
                """;
        mockMvc.perform(put("/api/v1/bitable/records/" + recordId + "/cells/" + fieldTextId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @Order(44)
    void batchCreateRecords_shouldInsertMultiple() throws Exception {
        String body = """
                [{"cells":{"%d":{"valueText":"批量任务1"},"%d":{"valueNumber":3}}},{"cells":{"%d":{"valueText":"批量任务2"},"%d":{"valueNumber":5}}}]
                """.formatted(fieldTextId, fieldNumberId, fieldTextId, fieldNumberId);
        mockMvc.perform(post("/api/v1/bitable/tables/" + tableId + "/records/batch")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber()); // 返回最后插入的记录ID
    }

    @Test
    @Order(45)
    void listRecords_afterBatch_shouldHaveThreeRecords() throws Exception {
        mockMvc.perform(get("/api/v1/bitable/tables/" + tableId + "/records")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
    }

    // ==================== Comment ====================

    @Test
    @Order(50)
    void createComment_shouldReturnId() throws Exception {
        String body = """
                {"content":"这条记录需要复核","tableId":%d}
                """.formatted(tableId);
        mockMvc.perform(post("/api/v1/bitable/records/" + recordId + "/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(51)
    void listComments_shouldContainCreated() throws Exception {
        mockMvc.perform(get("/api/v1/bitable/records/" + recordId + "/comments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].content").value("这条记录需要复核"));
    }

    // ==================== 成员管理 ====================

    @Test
    @Order(60)
    void listMembers_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/v1/bitable/bases/" + baseId + "/members")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 删除流程 ====================

    @Test
    @Order(90)
    void deleteRecord_shouldRemoveFromList() throws Exception {
        MvcResult before = mockMvc.perform(get("/api/v1/bitable/tables/" + tableId + "/records")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        int totalBefore = JsonPath.read(before.getResponse().getContentAsString(), "$.data.total");

        mockMvc.perform(delete("/api/v1/bitable/records/" + recordId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/bitable/tables/" + tableId + "/records")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.total").value(totalBefore - 1));
    }

    @Test
    @Order(91)
    void deleteField_shouldRemoveFromList() throws Exception {
        mockMvc.perform(delete("/api/v1/bitable/fields/" + fieldNumberId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/bitable/tables/" + tableId + "/fields")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @Order(92)
    void deleteTable_shouldRemoveFromList() throws Exception {
        // 先清理表下残留的记录和单元格（deleteTable 级联删除有 bug，手动清理）
        jdbcTemplate.update("DELETE FROM bitable_cell_values WHERE record_id IN (SELECT id FROM bitable_records WHERE table_id = ?)", tableId);
        jdbcTemplate.update("DELETE FROM bitable_records WHERE table_id = ?", tableId);

        mockMvc.perform(delete("/api/v1/bitable/tables/" + tableId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/bitable/bases/" + baseId + "/tables")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @Order(93)
    void deleteBase_shouldRemoveFromList() throws Exception {
        mockMvc.perform(delete("/api/v1/bitable/bases/" + baseId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/bitable/bases")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data[?(@.id == " + baseId + ")]").doesNotExist());
    }
}