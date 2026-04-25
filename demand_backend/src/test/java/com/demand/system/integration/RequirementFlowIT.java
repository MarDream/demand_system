package com.demand.system.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RequirementFlowIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createAndQueryRequirement_shouldWork() throws Exception {
        String token = loginAndGetAccessToken();

        String title = "IT-需求-" + UUID.randomUUID();
        String createBody = """
                {
                  "projectId": 1,
                  "title": "%s",
                  "description": "集成测试创建",
                  "type": "功能",
                  "priority": "P1"
                }
                """.formatted(title);

        mockMvc.perform(post("/api/v1/requirements")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/requirements")
                        .queryParam("projectId", "1")
                        .queryParam("keyword", title)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].title").value(title));
    }

    private String loginAndGetAccessToken() throws Exception {
        String loginBody = """
                {
                  "username": "admin",
                  "password": "admin123"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.accessToken");
    }
}

