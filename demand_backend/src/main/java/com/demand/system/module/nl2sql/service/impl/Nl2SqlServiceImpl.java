package com.demand.system.module.nl2sql.service.impl;

import com.demand.system.module.assistant.dto.AssistantTask;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.util.LlmJsonExtractor;
import com.demand.system.module.llm.constant.LlmApplicationCode;
import com.demand.system.module.llm.service.LlmModelResolver;
import com.demand.system.module.nl2sql.config.Nl2SqlProperties;
import com.demand.system.module.nl2sql.dto.DataChartSpec;
import com.demand.system.module.nl2sql.dto.DataQueryColumn;
import com.demand.system.module.nl2sql.dto.DataQueryResult;
import com.demand.system.module.nl2sql.dto.Nl2SqlContext;
import com.demand.system.module.nl2sql.dto.Nl2SqlOutcome;
import com.demand.system.module.nl2sql.dto.Nl2SqlPlan;
import com.demand.system.module.nl2sql.schema.SchemaCatalogService;
import com.demand.system.module.nl2sql.service.Nl2SqlAnswerStream;
import com.demand.system.module.nl2sql.service.Nl2SqlProgressListener;
import com.demand.system.module.nl2sql.service.Nl2SqlService;
import com.demand.system.module.nl2sql.support.Nl2SqlIntentDetector;
import com.demand.system.module.nl2sql.support.Nl2SqlPromptBuilder;
import com.demand.system.module.nl2sql.support.SqlExecutor;
import com.demand.system.module.nl2sql.support.SqlSafetyValidator;
import com.demand.system.module.nl2sql.support.SqlScopeInjector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NL2SQL 数据问答服务实现。
 *
 * <p>链路：问题理解与 SQL 生成（LLM）→ 安全校验（结构 + 白名单）→
 * 数据权限/软删除过滤注入 → 只读执行 → 整合性回答生成（LLM 流式）。</p>
 */
@Service
public class Nl2SqlServiceImpl implements Nl2SqlService {

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlServiceImpl.class);
    private static final String INTENT_DATA_QUERY = "data_query";
    private static final int MAX_SQL_RETRY = 1;

    private final Nl2SqlProperties properties;
    private final SchemaCatalogService schemaCatalogService;
    private final LlmGateway llmGateway;
    private final LlmModelResolver llmModelResolver;
    private final SqlExecutor sqlExecutor;
    private final ObjectMapper objectMapper;

    public Nl2SqlServiceImpl(Nl2SqlProperties properties,
                             SchemaCatalogService schemaCatalogService,
                             LlmGateway llmGateway,
                             LlmModelResolver llmModelResolver,
                             SqlExecutor sqlExecutor,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.schemaCatalogService = schemaCatalogService;
        this.llmGateway = llmGateway;
        this.llmModelResolver = llmModelResolver;
        this.sqlExecutor = sqlExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public boolean looksLikeDataQuery(String question) {
        if (!properties.isEnabled() || !properties.isAutoRouteEnabled()) {
            return false;
        }
        return Nl2SqlIntentDetector.looksLikeDataQuery(question);
    }

    @Override
    public Nl2SqlOutcome query(Nl2SqlContext context,
                               Nl2SqlProgressListener listener,
                               Nl2SqlAnswerStream answerStream) {
        if (!properties.isEnabled()) {
            return Nl2SqlOutcome.notDataQuery();
        }
        Nl2SqlProgressListener progress = listener == null ? Nl2SqlProgressListener.NOOP : listener;
        List<String> warnings = new ArrayList<>();

        // ===== Task 1: 问题理解 + SQL 生成 =====
        AssistantTask parseTask = new AssistantTask("nl2sql_generate", "理解问题并生成查询");
        parseTask.start("加载数据表结构，理解问题「" + shorten(context.question(), 30) + "」");
        progress.onTask(parseTask);

        SchemaCatalogService.CatalogSnapshot catalog = schemaCatalogService.snapshot();
        ResolvedModel chatModel = resolveChatModel(context.llmModelId());
        if (chatModel == null) {
            parseTask.fail("无可用模型，无法生成查询");
            progress.onTask(parseTask);
            String fallback = "数据问答需要配置可用的语言模型，请联系管理员在【系统设置 → 模型配置】中启用模型后重试。";
            streamText(answerStream, fallback);
            return Nl2SqlOutcome.success(
                    new DataQueryResult(context.question(), "", List.of(), List.of(), 0, false,
                            DataChartSpec.table("数据问答"), List.of(), 0, false),
                    List.of("无可用模型"));
        }

        String systemPrompt = Nl2SqlPromptBuilder.buildSqlSystemPrompt(
                catalog.schemaPrompt(), context.superAdmin(), properties.getMaxRows());

        Nl2SqlPlan plan = null;
        String validationError = null;
        SqlSafetyValidator.ValidationResult validation = null;
        for (int attempt = 0; attempt <= MAX_SQL_RETRY; attempt++) {
            String userPrompt = Nl2SqlPromptBuilder.buildSqlUserPrompt(
                    context.question(), context.history(), context.pageContext(), validationError);
            try {
                LlmGateway.ChatResult chatResult = llmGateway.chatWithProvider(
                        chatModel.provider(), systemPrompt, userPrompt,
                        chatModel.temperature(), chatModel.maxTokens());
                plan = parsePlan(chatResult.getContent());
            } catch (Exception e) {
                log.warn("NL2SQL 生成 SQL 失败: {}", e.getMessage(), e);
                parseTask.fail("模型调用失败：" + e.getMessage());
                progress.onTask(parseTask);
                return Nl2SqlOutcome.failure("模型调用失败：" + e.getMessage());
            }

            if (plan == null) {
                validationError = "模型返回内容无法解析为 JSON";
                continue;
            }
            if (!plan.needDatabase()) {
                parseTask.complete("该问题不需要查询数据库，转回常规助手处理");
                progress.onTask(parseTask);
                return Nl2SqlOutcome.notDataQuery();
            }
            validation = SqlSafetyValidator.validate(plan.sql(), catalog.tables().keySet(),
                    properties.getMaxRows(), properties.getDeniedColumnKeywords());
            if (validation.valid()) {
                break;
            }
            validationError = validation.errorMessage();
            plan = null;
        }

        if (plan == null || validation == null || !validation.valid()) {
            String reason = validationError == null ? "SQL 生成失败" : validationError;
            parseTask.fail("查询语句未通过安全校验：" + reason);
            progress.onTask(parseTask);
            String fallback = "抱歉，这个问题暂时无法转换为安全的数据查询（" + reason
                    + "）。可以换一种更具体的问法，例如“各状态的需求数量分布”。";
            streamText(answerStream, fallback);
            return Nl2SqlOutcome.success(
                    new DataQueryResult(context.question(), plan == null ? "" : nullToEmpty(plan.sql()),
                            List.of(), List.of(), 0, false,
                            DataChartSpec.table("数据问答"), List.of(), 0, false),
                    List.of(reason));
        }

        parseTask.complete("已生成查询：" + nullToEmpty(plan.title()));
        parseTask.log("info", "涉及数据表：" + String.join("、", validation.tables()));
        progress.onTask(parseTask);

        // ===== Task 2: 安全校验 + 数据权限注入 =====
        AssistantTask validateTask = new AssistantTask("nl2sql_validate", "安全校验与权限过滤");
        validateTask.start("校验只读语句、表白名单与行级数据范围");
        validateTask.log("info", "表结构校验通过，引用表：" + String.join("、", validation.tables()));

        boolean scopeApplied = false;
        String executableSql = validation.sql();
        if (!context.superAdmin() && context.visibleOrgIds() != null
                && context.visibleOrgIds().isEmpty()
                && hasOrgScopedTable(validation.tables(), catalog)) {
            // 普通用户但没有任何可见组织 → 不允许返回任何业务数据
            validateTask.fail("当前账号未配置数据权限范围，已阻止查询");
            progress.onTask(validateTask);
            String fallback = "当前账号未配置数据权限（组织范围），暂时无法查询业务数据。"
                    + "请联系管理员在【系统设置 → 角色管理】中为你的角色配置组织数据范围。";
            streamText(answerStream, fallback);
            return Nl2SqlOutcome.success(
                    new DataQueryResult(context.question(), executableSql, List.of(), List.of(), 0, false,
                            DataChartSpec.table(nullToEmpty(plan.title())), List.copyOf(validation.tables()), 0, false),
                    List.of("未配置数据权限范围"));
        }

        List<String> predicates = SqlScopeInjector.buildPredicates(
                validation.tables(),
                validation.aliasByTable(),
                catalog.softDeleteTables(),
                catalog.orgScopedTables(),
                context.visibleOrgIds(),
                context.superAdmin());
        if (!predicates.isEmpty()) {
            executableSql = SqlScopeInjector.inject(executableSql, predicates);
            validateTask.log("info", "已强制注入过滤条件：" + String.join("；", predicates));
        }
        if (!context.superAdmin() && !predicates.isEmpty()) {
            scopeApplied = true;
            warnings.add("已按你的数据权限范围过滤结果");
        }
        validateTask.complete("安全校验通过");
        progress.onTask(validateTask);

        // ===== Task 3: 执行查询 =====
        AssistantTask executeTask = new AssistantTask("nl2sql_execute", "查询数据库");
        executeTask.start("执行只读查询…");
        progress.onTask(executeTask);

        SqlExecutor.ExecutionResult execution;
        try {
            execution = sqlExecutor.execute(executableSql, properties.getMaxRows(), properties.getQueryTimeoutSeconds());
        } catch (Exception e) {
            log.warn("NL2SQL 执行失败: sql={}, error={}", executableSql, e.getMessage(), e);
            executeTask.fail("查询执行失败：" + e.getMessage());
            progress.onTask(executeTask);
            String fallback = "数据查询执行失败：" + e.getMessage() + "。可以尝试换一种更简单的问法。";
            streamText(answerStream, fallback);
            return Nl2SqlOutcome.failure(fallback);
        }
        executeTask.complete("返回 " + execution.rows().size() + " 行，耗时 " + execution.durationMs() + " ms");
        progress.onTask(executeTask);
        if (execution.truncated()) {
            warnings.add("结果超过 " + properties.getMaxRows() + " 行，仅展示前 " + properties.getMaxRows() + " 行");
        }
        if (execution.rows().isEmpty()) {
            warnings.add("没有查询到符合条件的数据");
        }

        DataQueryResult dataResult = new DataQueryResult(
                context.question(),
                executableSql,
                execution.columns(),
                execution.rows(),
                execution.rows().size(),
                execution.truncated(),
                normalizeChart(plan.chart(), execution.columns(), plan.title()),
                List.copyOf(validation.tables()),
                execution.durationMs(),
                scopeApplied);

        // ===== Task 4: 生成整合性回答 =====
        AssistantTask answerTask = new AssistantTask("nl2sql_answer", "生成回答");
        answerTask.start("基于查询结果生成整合性回答…");
        progress.onTask(answerTask);

        // 先下发结构化结果，前端可立即渲染表格/图表，再逐字显示总结
        if (answerStream != null) {
            try {
                answerStream.onDataResult(dataResult);
            } catch (Exception e) {
                log.debug("下发数据结果事件失败", e);
            }
        }

        boolean answered = false;
        if (answerStream != null) {
            try {
                String answerSystem = Nl2SqlPromptBuilder.buildAnswerSystemPrompt(context.superAdmin());
                String answerUser = Nl2SqlPromptBuilder.buildAnswerUserPrompt(
                        context.question(), dataResult, properties.getSummaryRows(),
                        execution.truncated(), scopeApplied);
                Map<String, Object> thinkingParams =
                        llmGateway.buildThinkingParams(chatModel.provider(), chatModel.maxTokens());
                llmGateway.streamChatWithProvider(
                        chatModel.provider(), answerSystem, answerUser,
                        chatModel.temperature(), chatModel.maxTokens(),
                        thinkingParams,
                        token -> answerStream.onToken(token),
                        token -> answerStream.onReasoning(token),
                        usage -> answerStream.onUsage(usage));
                answered = true;
                answerTask.complete("回答生成完成");
            } catch (Exception e) {
                log.warn("NL2SQL 回答生成失败，降级为结构化摘要: {}", e.getMessage(), e);
                answerTask.log("warn", "模型生成失败，已降级为结构化摘要");
            }
        }

        if (!answered && answerStream != null) {
            streamText(answerStream, buildDeterministicAnswer(dataResult, execution.truncated(), scopeApplied));
            answerTask.complete("已返回结构化摘要（降级）");
        }
        progress.onTask(answerTask);

        log.info("NL2SQL 完成：问题={}，表={}，行数={}", shorten(context.question(), 40),
                validation.tables(), execution.rows().size());
        return Nl2SqlOutcome.success(dataResult, warnings);
    }

    // ==================== 内部方法 ====================

    private boolean hasOrgScopedTable(Set<String> tables, SchemaCatalogService.CatalogSnapshot catalog) {
        Set<String> orgScoped = catalog.orgScopedTables();
        return tables.stream().anyMatch(orgScoped::contains);
    }

    private DataChartSpec normalizeChart(DataChartSpec chart, List<DataQueryColumn> columns, String title) {
        if (chart == null || !chart.isChartable()) {
            return DataChartSpec.table(title == null ? "查询结果" : title);
        }
        String category = matchColumn(chart.categoryField(), columns);
        String value = matchColumn(chart.valueField(), columns);
        String series = chart.seriesField() == null ? null : matchColumn(chart.seriesField(), columns);
        if (category == null || value == null) {
            return DataChartSpec.table(title == null ? "查询结果" : title);
        }
        String type = chart.type() == null ? "bar" : chart.type().toLowerCase();
        if (!Set.of("bar", "line", "pie", "table").contains(type)) {
            type = "bar";
        }
        return new DataChartSpec(type, chart.title() == null ? title : chart.title(), category, value, series);
    }

    private String matchColumn(String field, List<DataQueryColumn> columns) {
        if (field == null) {
            return null;
        }
        for (DataQueryColumn column : columns) {
            if (column.field().equalsIgnoreCase(field.trim())) {
                return column.field();
            }
        }
        // 容错：模型可能带上别名修饰（如 t.状态）
        String simple = field.contains(".") ? field.substring(field.lastIndexOf('.') + 1) : field;
        for (DataQueryColumn column : columns) {
            if (column.field().equalsIgnoreCase(simple.trim())) {
                return column.field();
            }
        }
        return null;
    }

    private Nl2SqlPlan parsePlan(String content) {
        String json = LlmJsonExtractor.extract(content);
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String sql = root.path("sql").asText(null);
            JsonNode needNode = root.path("needDatabase");
            // 模型偶发漏写 needDatabase：若给出了 SQL，则按"需要查库"处理，避免误判为非数据问题
            boolean needDatabase = needNode.isBoolean()
                    ? needNode.asBoolean()
                    : (sql != null && !sql.isBlank());
            String reasoning = root.path("reasoning").asText(null);
            String title = root.path("title").asText(null);
            String answerHint = root.path("answerHint").asText(null);
            DataChartSpec chart = null;
            JsonNode chartNode = root.path("chart");
            if (chartNode.isObject()) {
                chart = new DataChartSpec(
                        chartNode.path("type").asText("table"),
                        chartNode.path("title").asText(null),
                        nullableText(chartNode, "categoryField"),
                        nullableText(chartNode, "valueField"),
                        nullableText(chartNode, "seriesField"));
            }
            List<String> usedTables = new ArrayList<>();
            JsonNode tablesNode = root.path("usedTables");
            if (tablesNode.isArray()) {
                tablesNode.forEach(node -> usedTables.add(node.asText()));
            }
            return new Nl2SqlPlan(needDatabase, reasoning, sql, title, chart, answerHint, usedTables);
        } catch (Exception e) {
            log.warn("NL2SQL 计划 JSON 解析失败: {}", json, e);
            return null;
        }
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return (text == null || text.isBlank()) ? null : text;
    }

    /** 无模型或生成失败时的结构化兜底回答。 */
    private String buildDeterministicAnswer(DataQueryResult result, boolean truncated, boolean scopeApplied) {
        StringBuilder sb = new StringBuilder();
        if (result.rows().isEmpty()) {
            sb.append("没有查询到符合条件的数据。");
            if (scopeApplied) {
                sb.append("（结果已按你的数据权限范围过滤）");
            }
            sb.append("\n可以尝试放宽查询条件，例如扩大时间范围或去掉部分筛选条件。");
            return sb.toString();
        }
        sb.append("共查询到 ").append(result.rowCount()).append(" 条结果。\n\n");
        List<DataQueryColumn> columns = result.columns();
        sb.append("| ").append(columns.stream().map(DataQueryColumn::label).reduce((a, b) -> a + " | " + b).orElse("")).append(" |\n");
        sb.append("|").append(" --- |".repeat(columns.size())).append("\n");
        int limit = Math.min(properties.getSummaryRows(), result.rows().size());
        for (int i = 0; i < limit; i++) {
            Map<String, Object> row = result.rows().get(i);
            sb.append("| ");
            for (int c = 0; c < columns.size(); c++) {
                Object value = row.get(columns.get(c).field());
                sb.append(value == null ? "-" : String.valueOf(value));
                if (c < columns.size() - 1) {
                    sb.append(" | ");
                }
            }
            sb.append(" |\n");
        }
        if (result.rows().size() > limit) {
            sb.append("\n（仅展示前 ").append(limit).append(" 行）");
        }
        if (truncated) {
            sb.append("\n结果已按上限截断。");
        }
        if (scopeApplied) {
            sb.append("\n结果已按你的数据权限范围过滤。");
        }
        return sb.toString();
    }

    private void streamText(Nl2SqlAnswerStream stream, String text) {
        if (stream == null || text == null || text.isBlank()) {
            return;
        }
        int chunkSize = 24;
        for (int index = 0; index < text.length(); index += chunkSize) {
            stream.onToken(text.substring(index, Math.min(index + chunkSize, text.length())));
        }
    }

    private ResolvedModel resolveChatModel(Long llmModelId) {
        try {
            LlmModelResolver.ResolvedModel resolved = null;
            if (llmModelId != null) {
                resolved = llmModelResolver.resolveModel(llmModelId, LlmApplicationCode.ASSISTANT_NL2SQL);
                if (resolved == null) {
                    resolved = llmModelResolver.resolveModel(llmModelId, LlmApplicationCode.ASSISTANT_CHAT);
                }
            }
            if (resolved == null) {
                // 回退顺序必须"先看 assistant.chat 的绑定，再看该类型的默认模型"：
                // assistant.nl2sql 出厂时 model_id 为空，而 resolveFirst 在"应用未绑定模型"时
                // 会直接取该类型下 is_default=1 的全局默认模型 —— 那个模型可能和
                // assistant.chat 绑定的模型属于不同接入组（本项目全局默认模型在智谱、
                // assistant.chat 绑的是 Minimax），照单全收会让数据问答用错 provider。
                // 所以只有"应用确实显式绑定了模型"时才采纳 resolveFirst 的结果。
                LlmModelResolver.ResolvedModel nl2SqlFirst =
                        llmModelResolver.resolveFirst(LlmApplicationCode.ASSISTANT_NL2SQL);
                if (isExplicitlyBound(nl2SqlFirst)) {
                    resolved = nl2SqlFirst;
                }
            }
            if (resolved == null) {
                resolved = llmModelResolver.resolveFirst(LlmApplicationCode.ASSISTANT_CHAT);
            }
            if (resolved == null) {
                // assistant.chat 也没绑定时，退回该类型默认模型，保证未配置也能用
                resolved = llmModelResolver.resolveFirst(LlmApplicationCode.ASSISTANT_NL2SQL);
            }
            if (resolved == null) {
                return null;
            }
            return new ResolvedModel(
                    llmModelResolver.toGatewayProvider(resolved),
                    resolved.model().getTemperature(),
                    resolved.model().getMaxTokens());
        } catch (Exception e) {
            log.warn("NL2SQL 解析模型失败", e);
            return null;
        }
    }

    /** 判断解析结果是否就是该功能点显式绑定的那个模型（而非该类型下的默认模型）。 */
    private boolean isExplicitlyBound(LlmModelResolver.ResolvedModel resolved) {
        if (resolved == null || resolved.application() == null) {
            return false;
        }
        Long boundId = resolved.application().getModelId();
        return boundId != null && boundId.equals(resolved.model().getId());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String shorten(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    /** 已解析的聊天模型（provider + 采样参数）。 */
    private record ResolvedModel(LlmGatewayConfig.Provider provider,
                                 java.math.BigDecimal temperature,
                                 Integer maxTokens) {
    }
}
