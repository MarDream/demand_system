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
                fieldService.createField(tableId, fieldDTO);
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

        // 读取记录上下文
        BitableRecordVO record = recordService.getRecordById(recordId);
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
                targetField.getAiPrompt() != null ? "AI 提示词: " + targetField.getAiPrompt() : "");

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
            cellValue.setValueDate(java.time.LocalDate.parse(fillValue));
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
            Long targetFieldId = fieldService.createField(tableId, fieldDTO);

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
    @Transactional(rollbackFor = Exception.class)
    public void summarizeRecords(Long tableId, Long sourceFieldId, String targetFieldName, Long userId) {
        // 读取所有记录（Phase 3 取前200条）
        PageResult<BitableRecordVO> pageResult = recordService.listRecords(tableId, 1, 200);
        List<BitableRecordVO> records = pageResult.getList();

        // 创建目标字段（文本类型）
        BitableFieldCreateDTO fieldDTO = new BitableFieldCreateDTO();
        fieldDTO.setName(targetFieldName);
        fieldDTO.setFieldType("text");
        Long targetFieldId = fieldService.createField(tableId, fieldDTO);

        // 逐条调用 LLM 生成摘要（简化实现）
        for (BitableRecordVO r : records) {
            if (r.getCells() == null) continue;
            BitableCellValueVO cell = r.getCells().get(sourceFieldId);
            if (cell == null || cell.getValueText() == null || cell.getValueText().isBlank()) continue;

            String systemPrompt = "你是一个文本摘要生成助手。将输入文本压缩为简短摘要，保留关键信息。直接输出摘要文本，不要JSON格式。";
            String userMessage = "请为以下文本生成摘要：\n" + cell.getValueText();

            String summary = callChat(systemPrompt, userMessage);
            summary = cleanJsonResponse(summary).trim();
            // 截断过长摘要
            if (summary.length() > 500) {
                summary = summary.substring(0, 500);
            }

            CellValueDTO cellValue = new CellValueDTO();
            cellValue.setValueText(summary);
            recordService.updateCell(r.getId(), targetFieldId, cellValue, r.getVersion(), userId);
        }
    }
}
