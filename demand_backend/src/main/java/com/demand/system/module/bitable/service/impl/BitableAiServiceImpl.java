package com.demand.system.module.bitable.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.bitable.constant.FieldType;
import com.demand.system.module.bitable.dto.*;
import com.demand.system.module.bitable.service.BitableAiService;
import com.demand.system.module.bitable.service.BitableFieldService;
import com.demand.system.module.bitable.service.BitableRecordService;
import com.demand.system.module.bitable.service.BitableTableService;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.llm.constant.LlmApplicationCode;
import com.demand.system.module.llm.service.LlmModelResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多维表格 AI 能力 Service 实现
 */
@Service
public class BitableAiServiceImpl implements BitableAiService {

    private static final Logger log = LoggerFactory.getLogger(BitableAiServiceImpl.class);

    private final LlmGateway llmGateway;
    private final LlmModelResolver llmModelResolver;
    private final ObjectMapper objectMapper;
    private final BitableTableService tableService;
    private final BitableFieldService fieldService;
    private final BitableRecordService recordService;
    private final RabbitTemplate rabbitTemplate;

    public BitableAiServiceImpl(LlmGateway llmGateway,
                                LlmModelResolver llmModelResolver,
                                ObjectMapper objectMapper,
                                BitableTableService tableService,
                                BitableFieldService fieldService,
                                BitableRecordService recordService,
                                RabbitTemplate rabbitTemplate) {
        this.llmGateway = llmGateway;
        this.llmModelResolver = llmModelResolver;
        this.objectMapper = objectMapper;
        this.tableService = tableService;
        this.fieldService = fieldService;
        this.recordService = recordService;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ==================== Chat Provider 解析 ====================

    private LlmGatewayConfig.Provider resolveChatProvider() {
        LlmModelResolver.ResolvedModel resolved = llmModelResolver.resolveFirst(LlmApplicationCode.BITABLE_AI);
        if (resolved == null) {
            throw new BusinessException("请先配置可用的多维表格 AI 对话模型");
        }
        return llmModelResolver.toGatewayProvider(resolved);
    }

    private String callChat(String systemPrompt, String userMessage) {
        LlmGatewayConfig.Provider provider = resolveChatProvider();
        LlmGateway.ChatResult result = llmGateway.chatWithProvider(provider, systemPrompt, userMessage);
        return result.getContent();
    }

    /**
     * 清理 LLM 返回的 JSON 文本（移除 markdown 代码块包裹）
     */
    private String cleanJsonResponse(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3).trim();
            }
        }
        return s;
    }

    /**
     * 将 AI 提示词中的 {{字段名}} 占位符替换为当前记录对应字段的值；
     * 字段名未命中时占位符保持原样（LLM 仍可结合上下文推断）。
     */
    private String resolvePromptPlaceholders(String prompt, List<BitableFieldVO> fields, BitableRecordVO record) {
        if (prompt == null || !prompt.contains("{{")) {
            return prompt;
        }
        Map<String, String> valueByName = new HashMap<>();
        for (BitableFieldVO f : fields) {
            BitableCellValueVO cell = record.getCells() != null ? record.getCells().get(f.getId()) : null;
            valueByName.put(f.getName(), cell != null && cell.getValueText() != null ? cell.getValueText() : "");
        }
        Matcher matcher = Pattern.compile("\\{\\{([^{}]+)}}").matcher(prompt);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String replacement = valueByName.getOrDefault(name, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ==================== AI 建表 ====================

    @Override
    public AiBuildTableResult previewBuildTable(String description) {
        // 构建字段类型清单供 LLM 参考
        StringBuilder fieldTypeList = new StringBuilder();
        for (FieldType ft : FieldType.values()) {
            fieldTypeList.append(String.format("- %s (%s)\n", ft.getCode(), ft.getLabel()));
        }

        String systemPrompt = """
                你是一个多维表格结构设计助手。根据用户的自然语言描述，设计合适的数据表结构。

                可用的字段类型如下：
                %s

                输出格式要求（严格 JSON）：
                {
                  "tableName": "数据表名称",
                  "tableDescription": "数据表说明",
                  "fields": [
                    {
                      "name": "字段名称",
                      "fieldType": "字段类型code（从上面的列表中选）",
                      "config": "字段配置JSON（如单选/多选的选项列表）",
                      "description": "字段说明"
                    }
                  ]
                }

                重要：
                1. 必须直接返回 JSON，不要额外解释
                2. fieldType 必须从上面的字段类型列表中选择
                3. 单选/多选字段 config 格式为 {"options":[{"label":"选项1"},{"label":"选项2"}]}
                4. 一般至少包含一个 text 字段作为主字段
                """.formatted(fieldTypeList);

        String userMessage = "请帮我设计一个数据表：\n" + description;

        String rawResponse = callChat(systemPrompt, userMessage);
        String cleaned = cleanJsonResponse(rawResponse);

        try {
            return objectMapper.readValue(cleaned, AiBuildTableResult.class);
        } catch (Exception e) {
            log.error("解析AI建表结果失败: {}", cleaned, e);
            // 尝试提取 JSON 片段
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readValue(cleaned.substring(start, end + 1), AiBuildTableResult.class);
                } catch (Exception ex) {
                    log.debug("JSON 片段提取失败，尝试下一个候选区间", ex);
                }
            }
            throw new BusinessException("AI 生成的表结构格式异常，请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long confirmBuildTable(Long baseId, AiBuildTableResult result, Long userId) {
        if (result == null || result.getTableName() == null || result.getTableName().isBlank()) {
            throw new BusinessException("建表结果无效");
        }

        // 创建数据表
        BitableTableCreateDTO tableDTO = new BitableTableCreateDTO();
        tableDTO.setName(result.getTableName());
        tableDTO.setDescription(result.getTableDescription());
        Long tableId = tableService.createTable(baseId, tableDTO, userId);

        // 创建字段
        if (result.getFields() != null) {
            for (AiBuildTableResult.FieldDefinition fieldDef : result.getFields()) {
                BitableFieldCreateDTO fieldDTO = new BitableFieldCreateDTO();
                fieldDTO.setName(fieldDef.getName());
                fieldDTO.setFieldType(fieldDef.getFieldType());
                fieldDTO.setConfig(fieldDef.getConfig());
                fieldService.createField(tableId, fieldDTO, userId);
            }
        }

        return tableId;
    }

    // ==================== AI 智能填充 ====================

    @Override
    public Object fillCell(Long tableId, Long recordId, Long fieldId, Long userId) {
        // 读取字段信息
        List<BitableFieldVO> fields = fieldService.listFields(tableId);
        BitableFieldVO targetField = null;
        StringBuilder fieldListBuilder = new StringBuilder();
        for (BitableFieldVO f : fields) {
            fieldListBuilder.append(String.format("- %s (类型: %s, ID: %s)\n", f.getName(), f.getFieldType(), f.getId()));
            if (f.getId().equals(fieldId)) {
                targetField = f;
            }
        }

        if (targetField == null) {
            throw new BusinessException("目标字段不存在");
        }

        // 读取记录上下文（必须校验记录归属，防止跨表读写）
        BitableRecordVO record = recordService.getRecordById(recordId);
        if (record == null || !tableId.equals(record.getTableId())) {
            throw new BusinessException("记录不属于当前数据表");
        }
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("当前记录的字段值：\n");
        if (record.getCells() != null) {
            for (BitableFieldVO f : fields) {
                BitableCellValueVO cell = record.getCells().get(f.getId());
                String valueText = cell != null ? cell.getValueText() : "(空)";
                contextBuilder.append(String.format("- %s: %s\n", f.getName(), valueText));
            }
        }

        String systemPrompt = """
                你是一个多维表格智能填充助手。根据记录的上下文信息，为指定字段生成合适的值。

                字段列表：
                %s

                目标字段：%s (类型: %s)
                %s

                输出要求：
                1. 直接输出填充值（纯文本，不要JSON包裹）
                2. 如果目标字段是 single_select/multi_select，输出选项文本
                3. 如果目标字段是 number，输出纯数字
                4. 如果无法推断合理值，输出空字符串
                """.formatted(fieldListBuilder, targetField.getName(), targetField.getFieldType(),
                targetField.getAiPrompt() != null
                        ? "AI 提示词: " + resolvePromptPlaceholders(targetField.getAiPrompt(), fields, record)
                        : "");

        String userMessage = contextBuilder.toString();

        String fillValue = callChat(systemPrompt, userMessage);
        fillValue = cleanJsonResponse(fillValue).trim();

        // 更新单元格
        CellValueDTO cellValue = new CellValueDTO();
        if ("number".equals(targetField.getFieldType())) {
            try {
                cellValue.setValueNumber(new java.math.BigDecimal(fillValue));
            } catch (NumberFormatException e) {
                cellValue.setValueText(fillValue);
            }
        } else if ("date".equals(targetField.getFieldType())) {
            cellValue.setValueDate(fillValue);
        } else {
            cellValue.setValueText(fillValue);
        }

        BitableRecordCreateDTO updateDTO = new BitableRecordCreateDTO();
        Map<Long, CellValueDTO> cells = new HashMap<>();
        cells.put(fieldId, cellValue);
        updateDTO.setCells(cells);

        Integer newVersion = recordService.updateCell(recordId, fieldId, cellValue, record.getVersion(), userId);
        return Map.of("value", fillValue, "version", newVersion);
    }

    @Override
    public void fillBatchAsync(Long tableId, Long fieldId, Long userId) {
        Map<String, Object> message = new HashMap<>();
        message.put("tableId", tableId);
        message.put("fieldId", fieldId);
        message.put("userId", userId);
        rabbitTemplate.convertAndSend("bitable.ai.fill.batch", message);
        log.info("AI批量填充任务已发送到MQ: tableId={}, fieldId={}, userId={}", tableId, fieldId, userId);
    }

    @Override
    public void processFillBatch(Long tableId, Long fieldId, Long userId) {
        // 批量上限与单条失败跳过：LLM 调用耗时长且不稳定，逐条独立处理避免整体回滚
        final int maxRecords = 200;
        PageResult<BitableRecordVO> pageResult = recordService.listRecords(tableId, 1, maxRecords);
        List<BitableRecordVO> records = pageResult.getList();

        int succeeded = 0;
        int failed = 0;
        for (BitableRecordVO record : records) {
            try {
                fillCell(tableId, record.getId(), fieldId, userId);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("AI批量填充单条失败，跳过: tableId={}, recordId={}, fieldId={}, error={}",
                        tableId, record.getId(), fieldId, e.getMessage());
            }
        }
        log.info("AI批量填充完成: tableId={}, fieldId={}, 总数={}, 成功={}, 失败={}",
                tableId, fieldId, records.size(), succeeded, failed);
    }

    // ==================== AI 对话式查询 ====================

    @Override
    public AiQueryResult query(Long baseId, Long tableId, String question, Long userId) {
        // 如果指定了 tableId，限定查询范围
        Long queryTableId = tableId;
        if (queryTableId == null) {
            // 若未指定，取 base 下第一个表
            List<BitableTableVO> tables = tableService.listTables(baseId);
            if (tables.isEmpty()) {
                throw new BusinessException("该 Base 下没有数据表");
            }
            queryTableId = tables.get(0).getId();
        } else {
            // 校验表归属，防止跨 Base 读取数据
            BitableTableVO table = tableService.getTableById(queryTableId);
            if (table == null || !baseId.equals(table.getBaseId())) {
                throw new BusinessException("数据表不属于当前多维表格");
            }
        }

        // 读取字段列表
        List<BitableFieldVO> fields = fieldService.listFields(queryTableId);
        StringBuilder fieldListBuilder = new StringBuilder();
        for (BitableFieldVO f : fields) {
            fieldListBuilder.append(String.format("- %s (类型: %s, ID: %s)\n", f.getName(), f.getFieldType(), f.getId()));
        }

        // 读取所有记录（Phase 3 简化：取前100条）
        PageResult<BitableRecordVO> pageResult = recordService.listRecords(queryTableId, 1, 100);
        List<BitableRecordVO> records = pageResult.getList();

        StringBuilder dataBuilder = new StringBuilder();
        dataBuilder.append("数据记录：\n");
        for (int i = 0; i < records.size(); i++) {
            BitableRecordVO r = records.get(i);
            dataBuilder.append(String.format("记录%d (ID:%d): ", i + 1, r.getId()));
            if (r.getCells() != null) {
                for (BitableFieldVO f : fields) {
                    BitableCellValueVO cell = r.getCells().get(f.getId());
                    String val = cell != null ? cell.getValueText() : "";
                    dataBuilder.append(String.format("%s=%s; ", f.getName(), val));
                }
            }
            dataBuilder.append("\n");
        }

        String systemPrompt = """
                你是一个多维表格数据查询助手。根据用户的提问，分析数据并给出答案。

                字段列表：
                %s

                输出格式要求（严格 JSON）：
                {
                  "answer": "对用户问题的回答",
                  "matchedRecordIds": [匹配的记录ID列表]
                }

                重要：
                1. 必须直接返回 JSON
                2. answer 要简洁明了
                3. matchedRecordIds 是与问题相关的记录 ID 数组
                """.formatted(fieldListBuilder);

        String userMessage = question + "\n\n" + dataBuilder;

        String rawResponse = callChat(systemPrompt, userMessage);
        String cleaned = cleanJsonResponse(rawResponse);

        try {
            Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
            AiQueryResult result = new AiQueryResult();
            result.setAnswer((String) parsed.get("answer"));

            List<Long> matchedIds = new ArrayList<>();
            Object idsObj = parsed.get("matchedRecordIds");
            if (idsObj instanceof List<?> list) {
                for (Object id : list) {
                    matchedIds.add(((Number) id).longValue());
                }
            }

            List<AiQueryResult.RecordMatch> matches = new ArrayList<>();
            for (BitableRecordVO r : records) {
                if (matchedIds.contains(r.getId())) {
                    AiQueryResult.RecordMatch match = new AiQueryResult.RecordMatch();
                    match.setRecordId(r.getId());
                    // 用第一个 text 字段作为展示文本
                    String displayText = "";
                    if (r.getCells() != null) {
                        for (BitableFieldVO f : fields) {
                            BitableCellValueVO cell = r.getCells().get(f.getId());
                            if (cell != null && "text".equals(f.getFieldType()) && cell.getValueText() != null) {
                                displayText = cell.getValueText();
                                break;
                            }
                        }
                    }
                    match.setDisplayText(displayText);
                    matches.add(match);
                }
            }
            result.setMatchedRecords(matches);
            return result;
        } catch (Exception e) {
            log.error("解析AI查询结果失败: {}", cleaned, e);
            AiQueryResult result = new AiQueryResult();
            result.setAnswer(cleaned);
            result.setMatchedRecords(List.of());
            return result;
        }
    }

    // ==================== AI 自动分类 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void classifyRecords(Long tableId, Long sourceFieldId, String targetFieldName, Long userId) {
        // 读取字段列表
        List<BitableFieldVO> fields = fieldService.listFields(tableId);

        // 读取所有记录（Phase 3 取前200条）
        PageResult<BitableRecordVO> pageResult = recordService.listRecords(tableId, 1, 200);
        List<BitableRecordVO> records = pageResult.getList();

        // 提取源字段文本值
        StringBuilder textListBuilder = new StringBuilder();
        List<BitableRecordVO> recordsWithText = new ArrayList<>();
        for (BitableRecordVO r : records) {
            if (r.getCells() != null) {
                BitableCellValueVO cell = r.getCells().get(sourceFieldId);
                if (cell != null && cell.getValueText() != null && !cell.getValueText().isBlank()) {
                    textListBuilder.append(String.format("ID%d: %s\n", r.getId(), cell.getValueText()));
                    recordsWithText.add(r);
                }
            }
        }

        if (recordsWithText.isEmpty()) {
            throw new BusinessException("源字段没有文本内容，无法分类");
        }

        // 限制条数避免 token 过长
        String textContent = textListBuilder.toString();
        if (textContent.length() > 8000) {
            textContent = textContent.substring(0, 8000) + "\n(内容已截断)";
        }

        String systemPrompt = """
                你是一个数据分类助手。根据每条记录的文本内容，将它们分类到合适的类别中。

                输出格式要求（严格 JSON）：
                {
                  "categories": ["类别1", "类别2", "类别3"],
                  "classification": {
                    "ID1": "类别1",
                    "ID2": "类别2"
                  }
                }

                重要：
                1. 类别数量建议 3-8 个，不要过多
                2. 类别名称简短明确
                3. 每条记录必须归入一个类别
                4. 必须直接返回 JSON
                """;

        String userMessage = "请对以下记录进行分类：\n" + textContent;

        String rawResponse = callChat(systemPrompt, userMessage);
        String cleaned = cleanJsonResponse(rawResponse);

        try {
            Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});

            // 创建目标字段（单选类型）
            List<String> categories = new ArrayList<>();
            Object catsObj = parsed.get("categories");
            if (catsObj instanceof List<?> list) {
                for (Object cat : list) {
                    categories.add(cat.toString());
                }
            }

            BitableFieldCreateDTO fieldDTO = new BitableFieldCreateDTO();
            fieldDTO.setName(targetFieldName);
            fieldDTO.setFieldType("single_select");
            // 构建选项配置
            List<Map<String, String>> options = new ArrayList<>();
            for (String cat : categories) {
                options.add(Map.of("label", cat));
            }
            fieldDTO.setConfig(objectMapper.writeValueAsString(Map.of("options", options)));
            Long targetFieldId = fieldService.createField(tableId, fieldDTO, userId);

            // 批量更新记录
            Map<String, String> classification = new HashMap<>();
            Object classObj = parsed.get("classification");
            if (classObj instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    classification.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }

            for (BitableRecordVO r : recordsWithText) {
                String key = "ID" + r.getId();
                String category = classification.get(key);
                if (category != null) {
                    CellValueDTO cellValue = new CellValueDTO();
                    cellValue.setValueText(category);
                    recordService.updateCell(r.getId(), targetFieldId, cellValue, r.getVersion(), userId);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI分类结果解析失败: {}", cleaned, e);
            throw new BusinessException("AI 分类结果格式异常，请重试");
        }
    }

    // ==================== AI 自动摘要 ====================

    @Override
    public void summarizeRecords(Long tableId, Long sourceFieldId, String targetFieldName, Long userId) {
        // 读取所有记录（Phase 3 取前200条）
        PageResult<BitableRecordVO> pageResult = recordService.listRecords(tableId, 1, 200);
        List<BitableRecordVO> records = pageResult.getList();

        // 创建目标字段（文本类型）
        BitableFieldCreateDTO fieldDTO = new BitableFieldCreateDTO();
        fieldDTO.setName(targetFieldName);
        fieldDTO.setFieldType("text");
        Long targetFieldId = fieldService.createField(tableId, fieldDTO, userId);

        // 逐条调用 LLM 生成摘要。
        // 注意：不能把整个循环包在事务里 —— LLM 调用耗时长会长期占用数据库连接，
        // 且中途任何一条乐观锁冲突都会导致全部回滚。这里逐条独立提交，单条失败仅记录并跳过。
        int failed = 0;
        for (BitableRecordVO r : records) {
            if (r.getCells() == null) continue;
            BitableCellValueVO cell = r.getCells().get(sourceFieldId);
            if (cell == null || cell.getValueText() == null || cell.getValueText().isBlank()) continue;

            try {
                String systemPrompt = "你是一个文本摘要生成助手。将输入文本压缩为简短摘要，保留关键信息。直接输出摘要文本，不要JSON格式。";
                String userMessage = "请为以下文本生成摘要：\n" + cell.getValueText();

                String summary = callChat(systemPrompt, userMessage);
                summary = cleanJsonResponse(summary).trim();
                // 截断过长摘要
                if (summary.length() > 500) {
                    summary = summary.substring(0, 500);
                }

                // 写入前重读版本号，避免长时间 LLM 调用期间记录被并发编辑导致乐观锁冲突
                BitableRecordVO latest = recordService.getRecordById(r.getId());
                if (latest == null) continue;

                CellValueDTO cellValue = new CellValueDTO();
                cellValue.setValueText(summary);
                recordService.updateCell(r.getId(), targetFieldId, cellValue, latest.getVersion(), userId);
            } catch (Exception e) {
                failed++;
                log.warn("AI摘要写入失败，跳过记录 recordId={}: {}", r.getId(), e.getMessage());
            }
        }
        if (failed > 0) {
            log.info("AI摘要完成，{} 条记录处理失败被跳过, tableId={}", failed, tableId);
        }
    }

    // ==================== AI 自然语言筛选 ====================

    /** 筛选 UI 不支持的复杂类型不参与 AI 生成 */
    private static final Set<String> FILTER_EXCLUDED_TYPES = Set.of(
            "formula", "lookup", "rollup", "attachment", "button", "date_range", "ai_text", "ai_select");
    private static final Set<String> NUMERIC_FILTER_FIELD_TYPES = Set.of("number", "currency", "progress", "rating");
    private static final Set<String> DATE_FILTER_FIELD_TYPES = Set.of("date", "created_time", "modified_time", "last_modified_time");
    private static final Set<String> SELECT_EQ_FILTER_TYPES = Set.of("single_select", "checkbox");

    @Override
    public Map<String, Object> aiGenerateFilter(Long tableId, String text, Long userId) {
        List<BitableFieldVO> fields = fieldService.listFields(tableId).stream()
                .filter(f -> !FILTER_EXCLUDED_TYPES.contains(f.getFieldType()))
                .toList();
        if (fields.isEmpty()) {
            throw new BusinessException("当前数据表没有可用于筛选的字段");
        }
        String tableName = "";
        try {
            BitableTableVO table = tableService.getTableById(tableId);
            if (table != null && table.getName() != null) {
                tableName = table.getName();
            }
        } catch (Exception ignored) {
        }
        String recordNoun = "记录（本表名：" + tableName + "）";

        StringBuilder fieldListBuilder = new StringBuilder();
        for (BitableFieldVO f : fields) {
            fieldListBuilder.append("- 字段ID: ").append(f.getId())
                    .append(", 名称: ").append(f.getName())
                    .append(", 类型: ").append(f.getFieldType());
            String options = extractSelectOptionLabels(f);
            if (!options.isEmpty()) {
                fieldListBuilder.append(", 可选值: ").append(options);
            }
            fieldListBuilder.append("\n");
        }

        String systemPrompt = """
                你是多维表格筛选条件生成助手。把用户的自然语言需求转换为结构化筛选条件 JSON。
                表中的每一行记录是一条「%s」，用户描述里的「XX中的需求」「XX状态的需求」等说法中，XX 指的就是某个字段的可选值。

                可用字段（fieldId 只能从这里取）：
                %s

                操作符规则（operator 必须与字段类型匹配，否则该条作废）：
                - 数值类 number/currency/progress/rating: eq, ne, gt, gte, lt, lte, between, is_empty, is_not_empty
                - 日期类 date/created_time/modified_time/last_modified_time: eq, ne, gt, gte, lt, lte, between, is_empty, is_not_empty
                - 单选 single_select 与复选框 checkbox: eq, ne, is_empty, is_not_empty
                - 多选 multi_select: contains, not_contains, is_empty, is_not_empty
                - 文本与其他 text/url/email/phone/department/user/created_by: eq, ne, contains, not_contains, is_empty, is_not_empty

                输出要求（严格 JSON，不要解释和代码块标记）：
                {"logic":"and","rules":[{"fieldId":123,"operator":"eq","value":"进行中","valueMin":null,"valueMax":null}]}
                - logic：用户说「且/同时/并且」用 and，「或/任一」用 or，未说明默认 and
                - value 必须是具体值（字符串或数字），绝不能是字段名；日期格式 yyyy-MM-dd
                - 单选/复选字段的 value 必须**逐字等于**该字段「可选值」列表中的某一项：
                  · 用户提到的说法（如「阻塞中」「进行中」「已完成」）只要出现在可选值列表里，就必须原样使用
                  · 可选值列表里没有的值严禁输出——宁可省略该条件，也绝不替换成相近的其他可选值
                - between 表示区间：value 填 null，valueMin/valueMax 分别填起止值
                - is_empty / is_not_empty 时 value 填 null
                - 相对日期（今天/最近7天）换算为具体日期区间（between）
                - 无法映射到可用字段的条件直接省略，禁止编造字段、值或 fieldId
                """.formatted(recordNoun, fieldListBuilder);

        String raw = callChat(systemPrompt, "用户筛选需求：" + text);
        com.fasterxml.jackson.databind.JsonNode root = parseJsonNode(raw);
        if (root == null || !root.has("rules") || !root.get("rules").isArray() || root.get("rules").isEmpty()) {
            throw new BusinessException("未能从描述中解析出筛选条件，请换个说法或手动配置");
        }

        String logic = "or".equalsIgnoreCase(root.path("logic").asText("and")) ? "or" : "and";
        Map<Long, BitableFieldVO> fieldById = new HashMap<>();
        for (BitableFieldVO f : fields) {
            fieldById.put(f.getId(), f);
        }

        List<Map<String, Object>> rules = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode ruleNode : root.get("rules")) {
            long fieldId = ruleNode.path("fieldId").asLong(0);
            BitableFieldVO field = fieldById.get(fieldId);
            if (field == null) {
                continue;
            }
            String operator = ruleNode.path("operator").asText("");
            if (!operatorAllowedForType(field.getFieldType(), operator)) {
                continue;
            }
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("fieldId", fieldId);
            rule.put("operator", operator);
            if ("between".equals(operator)) {
                String min = ruleNode.path("valueMin").asText("");
                String max = ruleNode.path("valueMax").asText("");
                if (min.isEmpty() || max.isEmpty()) {
                    continue;
                }
                rule.put("value", List.of(min, max));
                rule.put("valueMin", min);
                rule.put("valueMax", max);
            } else if (!"is_empty".equals(operator) && !"is_not_empty".equals(operator)) {
                String value = ruleNode.path("value").asText("");
                if (value.isEmpty()) {
                    continue;
                }
                // 防幻觉：单选字段 eq/ne 的值必须逐字等于某个可选值，否则丢弃该条（交给选项命中兜底）
                if ("single_select".equals(field.getFieldType())
                        && ("eq".equals(operator) || "ne".equals(operator))) {
                    Set<String> labels = extractOptionLabelSet(field);
                    if (!labels.isEmpty() && !labels.contains(value)) {
                        continue;
                    }
                }
                rule.put("value", value);
            }
            rules.add(rule);
        }
        if (rules.isEmpty()) {
            // 兜底：单选/多选字段的某个可选值逐字出现在用户原文中时，直接生成等值条件。
            // 解决「阻塞中的需求」这类只提到值、没提字段名的描述被 LLM 猜错值的问题。
            List<Map<String, Object>> fallback = matchSelectOptionsInText(text, fields);
            if (fallback.isEmpty()) {
                throw new BusinessException("未能从描述中解析出有效筛选条件，请换个说法或手动配置");
            }
            rules = fallback;
            logic = "and";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logic", logic);
        result.put("rules", rules);
        return result;
    }

    /** 操作符是否与字段类型匹配（与前端 FilterPanel 的 operatorsFor 对齐） */
    private boolean operatorAllowedForType(String fieldType, String operator) {
        boolean numeric = NUMERIC_FILTER_FIELD_TYPES.contains(fieldType);
        boolean dateLike = DATE_FILTER_FIELD_TYPES.contains(fieldType);
        return switch (operator) {
            case "eq", "ne", "is_empty", "is_not_empty" -> true;
            case "gt", "gte", "lt", "lte", "between" -> numeric || dateLike;
            case "contains", "not_contains" -> !numeric && !dateLike && !SELECT_EQ_FILTER_TYPES.contains(fieldType);
            default -> false;
        };
    }

    /** 单选/多选字段的可选值清单（供 LLM 把模糊说法映射到具体选项） */
    private String extractSelectOptionLabels(BitableFieldVO field) {
        Object config = field.getConfig();
        if (!(config instanceof Map<?, ?> map) || !(map.get("options") instanceof List<?> options)) {
            return "";
        }
        List<String> labels = new ArrayList<>();
        for (Object option : options) {
            if (option instanceof Map<?, ?> opt && opt.get("label") != null) {
                labels.add(String.valueOf(opt.get("label")));
            }
            if (labels.size() >= 20) {
                break;
            }
        }
        return String.join("/", labels);
    }

    /** 单选/复选字段的选项 label 集合（防幻觉校验用） */
    private Set<String> extractOptionLabelSet(BitableFieldVO field) {
        Set<String> labels = new HashSet<>();
        Object config = field.getConfig();
        if (config instanceof Map<?, ?> map && map.get("options") instanceof List<?> options) {
            for (Object option : options) {
                if (option instanceof Map<?, ?> opt && opt.get("label") != null) {
                    labels.add(String.valueOf(opt.get("label")));
                }
            }
        }
        return labels;
    }

    /**
     * 选项命中兜底：在用户原文中逐字查找单选/多选字段的选项值（label 完整出现，
     * 且紧邻前缀不是「未/非」避免反义误匹配）。一个字段命中多个选项时取原文中最早出现的。
     */
    private List<Map<String, Object>> matchSelectOptionsInText(String text, List<BitableFieldVO> fields) {
        List<Map<String, Object>> rules = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return rules;
        }
        for (BitableFieldVO field : fields) {
            String type = field.getFieldType();
            if (!"single_select".equals(type) && !"multi_select".equals(type) && !"checkbox".equals(type)) {
                continue;
            }
            String best = null;
            int bestIndex = Integer.MAX_VALUE;
            for (String label : extractOptionLabelSet(field)) {
                if (label.isBlank()) {
                    continue;
                }
                int idx = text.indexOf(label);
                while (idx >= 0) {
                    char prev = idx > 0 ? text.charAt(idx - 1) : '\0';
                    if (prev == '未' || prev == '非') {
                        idx = text.indexOf(label, idx + 1);
                        continue;
                    }
                    if (idx < bestIndex) {
                        bestIndex = idx;
                        best = label;
                    }
                    break;
                }
            }
            if (best != null) {
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("fieldId", field.getId());
                rule.put("operator", "multi_select".equals(type) ? "contains" : "eq");
                rule.put("value", best);
                rules.add(rule);
            }
        }
        return rules;
    }

    /** 容错解析 LLM 输出的 JSON（剥代码块、截取花括号片段） */
    private com.fasterxml.jackson.databind.JsonNode parseJsonNode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3).trim();
            }
        }
        try {
            return objectMapper.readTree(s);
        } catch (Exception ignored) {
            // 尝试片段提取
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return objectMapper.readTree(s.substring(start, end + 1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
