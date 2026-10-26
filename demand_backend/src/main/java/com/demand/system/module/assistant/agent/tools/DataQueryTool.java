package com.demand.system.module.assistant.agent.tools;

import com.demand.system.module.assistant.agent.core.AgentTool;
import com.demand.system.module.assistant.agent.core.AgentToolContext;
import com.demand.system.module.assistant.agent.core.AgentToolResult;
import com.demand.system.module.assistant.agent.core.ToolParamSchema;
import com.demand.system.module.nl2sql.dto.DataQueryResult;
import com.demand.system.module.nl2sql.dto.Nl2SqlContext;
import com.demand.system.module.nl2sql.dto.Nl2SqlOutcome;
import com.demand.system.module.nl2sql.service.Nl2SqlAnswerStream;
import com.demand.system.module.nl2sql.service.Nl2SqlProgressListener;
import com.demand.system.module.nl2sql.service.Nl2SqlService;
import com.demand.system.module.requirement.service.impl.RequirementServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** NL2SQL 数据查询工具：自然语言 → 只读 SQL → 结果集表格（数据权限与软删除过滤由服务端注入） */
@Component
public class DataQueryTool implements AgentTool {

    private final Nl2SqlService nl2SqlService;
    private final RequirementServiceImpl requirementService;

    public DataQueryTool(Nl2SqlService nl2SqlService, RequirementServiceImpl requirementService) {
        this.nl2SqlService = nl2SqlService;
        this.requirementService = requirementService;
    }

    @Override
    public String name() {
        return "data_query";
    }

    @Override
    public String title() {
        return "数据查询";
    }

    @Override
    public String description() {
        return "把自然语言转成只读 SQL 查询需求系统的业务数据库（需求、工单、迭代、用户等），返回结果表格。"
                + "适合「有多少/统计/分布/汇总/平均值/按条件筛选」类数据问题。一次只问一个明确的统计问题。";
    }

    @Override
    public Map<String, Object> schema() {
        return ToolParamSchema.object()
                .property("question", "string", "要查询的数据问题（一个明确、独立的统计或筛选问句）")
                .required("question")
                .build();
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode args) throws Exception {
        String question = args.path("question").asText("");
        if (question.isBlank()) {
            return AgentToolResult.text("查询失败：question 参数不能为空");
        }
        if (!nl2SqlService.isEnabled()) {
            return AgentToolResult.text("数据问答能力未启用（NL2SQL 未配置）。");
        }
        context.log("info", "数据查询：" + question);

        List<Long> visibleOrgIds = requirementService.resolveVisibleOrgIds(context.getUserId(), context.isSuperAdmin());
        Nl2SqlContext nl2SqlContext = new Nl2SqlContext(
                question,
                context.getHistory(),
                context.getLlmModelId(),
                context.getUserId(),
                context.isSuperAdmin(),
                visibleOrgIds,
                context.getPageContext());

        Nl2SqlOutcome outcome = nl2SqlService.query(nl2SqlContext, Nl2SqlProgressListener.NOOP, new Nl2SqlAnswerStream() {
            @Override
            public void onDataResult(DataQueryResult result) {
            }

            @Override
            public void onToken(String token) {
            }

            @Override
            public void onReasoning(String token) {
            }

            @Override
            public void onUsage(com.demand.system.module.knowledge.llm.LlmGateway.ChatUsage usage) {
            }
        });

        if (!outcome.isDataQuery()) {
            return AgentToolResult.text("该问题与数据库数据无关，未执行查询。");
        }
        if (!outcome.success()) {
            String reason = outcome.errorMessage() == null || outcome.errorMessage().isBlank()
                    ? "未知原因" : outcome.errorMessage();
            AgentToolResult.Builder builder = AgentToolResult.builder("数据查询失败：" + reason)
                    .uiSummary("查询失败");
            if (outcome.warnings() != null) {
                outcome.warnings().forEach(builder::warning);
            }
            return builder.build();
        }

        DataQueryResult result = outcome.dataResult();
        if (result == null || result.rows() == null || result.rows().isEmpty()) {
            return AgentToolResult.text("查询执行成功，但没有符合条件的数据（question=" + question + "）。");
        }
        AgentToolResult.Builder builder = AgentToolResult.builder(buildTable(result))
                .uiSummary("查询到 " + result.rowCount() + " 行数据")
                .dataResult(result);
        if (outcome.warnings() != null) {
            outcome.warnings().forEach(builder::warning);
        }
        return builder.build();
    }

    /** 结果集转 Markdown 表格（回传模型），行数截断到 10 行 */
    private String buildTable(DataQueryResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("查询问题：").append(result.question()).append('\n');
        if (result.sql() != null && !result.sql().isBlank()) {
            sb.append("执行 SQL：").append(result.sql().replaceAll("\\s+", " ")).append('\n');
        }
        List<String> headers = new ArrayList<>(result.rows().get(0).keySet());
        sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
        sb.append(headers.stream().map(h -> "---").collect(Collectors.joining(" | ", "| ", " |"))).append('\n');
        int cap = Math.min(result.rows().size(), 10);
        for (int i = 0; i < cap; i++) {
            Map<String, Object> row = result.rows().get(i);
            sb.append(headers.stream()
                    .map(h -> row.get(h) == null ? "" : String.valueOf(row.get(h)))
                    .collect(Collectors.joining(" | ", "| ", " |"))).append('\n');
        }
        if (result.rowCount() > cap) {
            sb.append("（共 ").append(result.rowCount()).append(" 行，仅展示前 ").append(cap).append(" 行）\n");
        }
        return sb.toString();
    }
}
