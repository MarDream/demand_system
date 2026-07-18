package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.config.BitableAutomationMQConfig;
import com.demand.system.module.bitable.dto.BitableAutomationCreateDTO;
import com.demand.system.module.bitable.dto.BitableAutomationUpdateDTO;
import com.demand.system.module.bitable.dto.BitableAutomationVO;
import com.demand.system.module.bitable.dto.BitableRecordCreateDTO;
import com.demand.system.module.bitable.dto.CellValueDTO;
import com.demand.system.module.bitable.entity.BitableAutomation;
import com.demand.system.module.bitable.entity.BitableAutomationRun;
import com.demand.system.module.bitable.mapper.BitableAutomationMapper;
import com.demand.system.module.bitable.mapper.BitableAutomationRunMapper;
import com.demand.system.module.bitable.service.BitableAutomationService;
import com.demand.system.module.bitable.service.BitableRecordService;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 多维表格-自动化规则 Service 实现
 */
@Service
public class BitableAutomationServiceImpl implements BitableAutomationService {

    private static final Logger log = LoggerFactory.getLogger(BitableAutomationServiceImpl.class);

    /** 单次链路内同一自动化最多触发的次数（防递归） */
    private static final int MAX_TRIGGER_DEPTH = 3;

    private final BitableAutomationMapper automationMapper;
    private final BitableAutomationRunMapper runMapper;
    private final BitableRecordService recordService;
    private final RabbitTemplate rabbitTemplate;

    public BitableAutomationServiceImpl(BitableAutomationMapper automationMapper,
                                        BitableAutomationRunMapper runMapper,
                                        BitableRecordService recordService,
                                        RabbitTemplate rabbitTemplate) {
        this.automationMapper = automationMapper;
        this.runMapper = runMapper;
        this.recordService = recordService;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ==================== CRUD ====================

    @Override
    public List<BitableAutomationVO> listAutomations(Long baseId) {
        List<BitableAutomation> automations = automationMapper.selectByBaseId(baseId);
        List<BitableAutomationVO> voList = new ArrayList<>();
        for (BitableAutomation automation : automations) {
            BitableAutomationVO vo = toVO(automation);
            // 填充最近一次执行状态
            BitableAutomationRun latestRun = runMapper.selectLatestByAutomationId(automation.getId());
            if (latestRun != null) {
                vo.setLastRunStatus(latestRun.getStatus());
                vo.setLastRunAt(latestRun.getCreatedAt() != null
                        ? latestRun.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        : null);
            }
            voList.add(vo);
        }
        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAutomation(Long baseId, BitableAutomationCreateDTO dto, Long userId) {
        validateTriggerType(dto.getTriggerType());
        validateActionType(dto.getActionType());

        BitableAutomation automation = new BitableAutomation();
        automation.setBaseId(baseId);
        automation.setTableId(dto.getTableId());
        automation.setName(dto.getName());
        automation.setStatus("enabled");
        automation.setTriggerType(dto.getTriggerType());
        automation.setTriggerConfig(BitableJsonUtils.toJsonString(dto.getTriggerConfig()));
        automation.setActionType(dto.getActionType());
        automation.setActionConfig(BitableJsonUtils.toJsonString(dto.getActionConfig()));
        automation.setCreatedBy(userId);
        automationMapper.insert(automation);
        return automation.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAutomation(Long id, BitableAutomationUpdateDTO dto) {
        BitableAutomation existing = automationMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("自动化规则不存在");
        }

        UpdateWrapper<BitableAutomation> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);

        if (dto.getName() != null) {
            wrapper.set("name", dto.getName());
        }
        if (dto.getStatus() != null) {
            if (!"enabled".equals(dto.getStatus()) && !"disabled".equals(dto.getStatus())) {
                throw new BusinessException("状态值无效，必须为 enabled 或 disabled");
            }
            wrapper.set("status", dto.getStatus());
        }
        if (dto.getTriggerConfig() != null) {
            wrapper.set("trigger_config", BitableJsonUtils.toJsonString(dto.getTriggerConfig()));
        }
        if (dto.getActionConfig() != null) {
            wrapper.set("action_config", BitableJsonUtils.toJsonString(dto.getActionConfig()));
        }

        automationMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAutomation(Long id) {
        BitableAutomation existing = automationMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("自动化规则不存在");
        }
        automationMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleAutomation(Long id, boolean enabled) {
        BitableAutomation existing = automationMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("自动化规则不存在");
        }

        UpdateWrapper<BitableAutomation> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).set("status", enabled ? "enabled" : "disabled");
        automationMapper.update(null, wrapper);
    }

    // ==================== 事件触发 ====================

    @Override
    public void onRecordChanged(Long tableId, Long recordId, String changeType, Map<String, Object> changedFields) {
        // 查询该表所有 enabled 状态的自动化规则
        List<BitableAutomation> automations = automationMapper.selectByTableIdAndStatus(tableId, "enabled");
        if (automations.isEmpty()) {
            return;
        }

        for (BitableAutomation automation : automations) {
            // 匹配触发类型
            if (!matchesTriggerType(automation.getTriggerType(), changeType)) {
                continue;
            }

            // 匹配触发条件（triggerConfig 中的 fieldConditions）
            if (!matchesTriggerConfig(automation.getTriggerConfig(), changeType, changedFields)) {
                continue;
            }

            // 构建幂等 eventId
            String eventId = buildEventId(automation.getId(), tableId, recordId, changeType);

            // 创建执行记录
            BitableAutomationRun run = new BitableAutomationRun();
            run.setAutomationId(automation.getId());
            run.setEventId(eventId);
            run.setStatus("pending");
            run.setTriggerDetail(BitableJsonUtils.toJsonString(Map.of(
                    "tableId", tableId,
                    "recordId", recordId,
                    "changeType", changeType,
                    "changedFields", changedFields != null ? changedFields : Collections.emptyMap()
            )));
            run.setAttempt(0);
            runMapper.insert(run);

            // 发送 MQ 消息（异步执行）
            Map<String, Object> context = new HashMap<>();
            context.put("tableId", tableId);
            context.put("recordId", recordId);
            context.put("changeType", changeType);
            context.put("changedFields", changedFields != null ? changedFields : Collections.emptyMap());
            // 防递归：携带触发链路信息
            context.put("originAutomationId", automation.getId());
            context.put("traceId", UUID.randomUUID().toString());
            context.put("triggerDepth", 1);

            Map<String, Object> message = new HashMap<>();
            message.put("automationId", automation.getId());
            message.put("runId", run.getId());
            message.put("context", context);

            try {
                rabbitTemplate.convertAndSend(
                        BitableAutomationMQConfig.AUTOMATION_EXCHANGE,
                        BitableAutomationMQConfig.AUTOMATION_ROUTING_KEY,
                        message
                );
                log.info("自动化任务已发送到MQ: automationId={}, runId={}, tableId={}, recordId={}",
                        automation.getId(), run.getId(), tableId, recordId);
            } catch (Exception e) {
                log.error("发送自动化MQ消息失败: automationId={}, runId={}", automation.getId(), run.getId(), e);
                // 更新执行记录为失败
                markRunFailed(run.getId(), "MQ_SEND_FAILED", "消息发送失败: " + e.getMessage());
            }
        }
    }

    // ==================== 动作执行（MQ消费者调用） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeAutomation(Long automationId, Long runId, Map<String, Object> context) {
        BitableAutomation automation = automationMapper.selectById(automationId);
        if (automation == null) {
            markRunFailed(runId, "AUTOMATION_NOT_FOUND", "自动化规则不存在");
            return;
        }

        // 防递归检查
        Integer triggerDepth = (Integer) context.getOrDefault("triggerDepth", 1);
        if (triggerDepth != null && triggerDepth > MAX_TRIGGER_DEPTH) {
            markRunFailed(runId, "MAX_DEPTH_EXCEEDED", "触发链路过深，已超过最大深度" + MAX_TRIGGER_DEPTH);
            return;
        }

        // 防递归：检查同一次链路内同一自动化是否已触发
        Long originAutomationId = asLong(context.get("originAutomationId"));
        if (originAutomationId != null && originAutomationId.equals(automationId)
                && triggerDepth != null && triggerDepth > 1) {
            log.info("自动化规则在同一链路内重复触发，跳过: automationId={}", automationId);
            markRunFailed(runId, "RECURSION_BLOCKED", "同一自动化在链路内重复触发，已阻止递归");
            return;
        }

        // 更新执行记录为 running
        UpdateWrapper<BitableAutomationRun> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", runId)
                .set("status", "running")
                .set("started_at", LocalDateTime.now())
                .set("attempt", ((Number) context.getOrDefault("attempt", 0)).intValue() + 1);
        runMapper.update(null, wrapper);

        try {
            Object actionResult = executeAction(automation, context);

            // 更新执行记录为 succeeded
            UpdateWrapper<BitableAutomationRun> successWrapper = new UpdateWrapper<>();
            successWrapper.eq("id", runId)
                    .set("status", "succeeded")
                    .set("action_result", BitableJsonUtils.toJsonString(actionResult))
                    .set("finished_at", LocalDateTime.now());
            runMapper.update(null, successWrapper);

            log.info("自动化执行成功: automationId={}, runId={}", automationId, runId);
        } catch (Exception e) {
            log.error("自动化执行失败: automationId={}, runId={}", automationId, runId, e);
            markRunFailed(runId, "ACTION_FAILED", e.getMessage());
        }
    }

    // ==================== 动作执行器 ====================

    private Object executeAction(BitableAutomation automation, Map<String, Object> context) {
        String actionType = automation.getActionType();
        @SuppressWarnings("unchecked")
        Map<String, Object> actionConfig = parseConfig(automation.getActionConfig());

        return switch (actionType) {
            case "update_record" -> executeUpdateRecord(actionConfig, context);
            case "create_record" -> executeCreateRecord(actionConfig, context);
            case "send_message" -> executeSendMessage(actionConfig, context);
            case "http_request" -> executeHttpRequest(actionConfig, context);
            default -> throw new BusinessException("不支持的动作类型: " + actionType);
        };
    }

    /**
     * 动作: 更新记录字段
     */
    private Object executeUpdateRecord(Map<String, Object> config, Map<String, Object> context) {
        Long recordId = asLong(config.get("recordId"));
        Long fieldId = asLong(config.get("fieldId"));

        // 如果未指定 recordId，使用触发记录
        if (recordId == null) {
            recordId = asLong(context.get("recordId"));
        }
        if (recordId == null) {
            throw new BusinessException("update_record 动作缺少 recordId");
        }
        if (fieldId == null) {
            throw new BusinessException("update_record 动作缺少 fieldId");
        }

        String valueText = (String) config.get("valueText");
        Object valueJson = config.get("valueJson");

        // 构建更新值
        Map<String, Object> valueMap = new HashMap<>();
        if (valueText != null) {
            valueMap.put("valueText", valueText);
        }
        if (valueJson != null) {
            valueMap.put("valueJson", valueJson);
        }

        // 获取当前记录版本号（乐观锁）
        var record = recordService.getRecordById(recordId);
        Integer version = record.getVersion();

        recordService.updateCell(recordId, fieldId, valueMap, version, 0L);
        return Map.of("recordId", recordId, "fieldId", fieldId, "updated", true);
    }

    /**
     * 动作: 创建记录
     */
    private Object executeCreateRecord(Map<String, Object> config, Map<String, Object> context) {
        Long targetTableId = asLong(config.get("targetTableId"));
        if (targetTableId == null) {
            // 默认使用触发记录所在表
            targetTableId = asLong(context.get("tableId"));
        }
        if (targetTableId == null) {
            throw new BusinessException("create_record 动作缺少 targetTableId");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> fieldsConfig = (Map<String, Object>) config.getOrDefault("fields", Collections.emptyMap());

        BitableRecordCreateDTO dto = new BitableRecordCreateDTO();
        Map<Long, CellValueDTO> cells = new HashMap<>();

        for (Map.Entry<String, Object> entry : fieldsConfig.entrySet()) {
            try {
                Long fieldId = Long.parseLong(entry.getKey());
                CellValueDTO cellValue = new CellValueDTO();
                if (entry.getValue() instanceof String s) {
                    cellValue.setValueText(s);
                } else if (entry.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) entry.getValue();
                    if (map.containsKey("valueText")) {
                        cellValue.setValueText((String) map.get("valueText"));
                    }
                    if (map.containsKey("valueJson")) {
                        cellValue.setValueJson(map.get("valueJson"));
                    }
                } else if (entry.getValue() != null) {
                    cellValue.setValueText(String.valueOf(entry.getValue()));
                }
                cells.put(fieldId, cellValue);
            } catch (NumberFormatException ignored) {
                // 跳过非数字键
            }
        }
        dto.setCells(cells);

        Long newRecordId = recordService.createRecord(targetTableId, dto, 0L);
        return Map.of("recordId", newRecordId, "tableId", targetTableId, "created", true);
    }

    /**
     * 动作: 发送消息（MVP阶段记录到操作日志）
     */
    private Object executeSendMessage(Map<String, Object> config, Map<String, Object> context) {
        String messageTemplate = (String) config.getOrDefault("message", "");
        String channel = (String) config.getOrDefault("channel", "log");

        // MVP: 仅记录日志，暂不实现真实推送
        log.info("[自动化消息] channel={}, message={}", channel, messageTemplate);
        return Map.of("channel", channel, "message", messageTemplate, "sent", true, "note", "MVP阶段仅记录日志");
    }

    /**
     * 动作: HTTP 请求
     */
    private Object executeHttpRequest(Map<String, Object> config, Map<String, Object> context) {
        String url = (String) config.get("url");
        if (url == null || url.isBlank()) {
            throw new BusinessException("http_request 动作缺少 url");
        }

        String method = (String) config.getOrDefault("method", "POST");
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) config.getOrDefault("headers", Collections.emptyMap());
        Object body = config.get("body");

        // 替换模板变量
        if (body instanceof String bodyStr) {
            body = replaceContextVariables(bodyStr, context);
        }
        String processedUrl = replaceContextVariables(url, context);

        try {
            org.springframework.http.HttpHeaders httpHeaders = new org.springframework.http.HttpHeaders();
            headers.forEach((k, v) -> httpHeaders.add(k, String.valueOf(v)));
            if (httpHeaders.get("Content-Type") == null || httpHeaders.get("Content-Type").isEmpty()) {
                httpHeaders.add("Content-Type", "application/json");
            }

            org.springframework.http.HttpEntity<Object> entity = new org.springframework.http.HttpEntity<>(body, httpHeaders);

            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            // 设置超时 10s
            var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(10_000);
            requestFactory.setReadTimeout(10_000);
            restTemplate.setRequestFactory(requestFactory);

            org.springframework.http.ResponseEntity<String> response;
            if ("GET".equalsIgnoreCase(method)) {
                response = restTemplate.exchange(processedUrl, org.springframework.http.HttpMethod.GET, entity, String.class);
            } else {
                response = restTemplate.exchange(processedUrl, org.springframework.http.HttpMethod.POST, entity, String.class);
            }

            // 限制响应大小（1MB）
            String responseBody = response.getBody();
            if (responseBody != null && responseBody.length() > 1_048_576) {
                responseBody = responseBody.substring(0, 1_048_576) + "...(truncated)";
            }

            return Map.of("statusCode", response.getStatusCode().value(), "response", responseBody);
        } catch (Exception e) {
            throw new BusinessException("HTTP请求执行失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private boolean matchesTriggerType(String automationTriggerType, String changeType) {
        return automationTriggerType.equals(changeType);
    }

    @SuppressWarnings("unchecked")
    private boolean matchesTriggerConfig(String triggerConfigJson, String changeType, Map<String, Object> changedFields) {
        if (triggerConfigJson == null || triggerConfigJson.isBlank()) {
            return true; // 无条件配置，默认匹配
        }

        Object parsed = BitableJsonUtils.parseJson(triggerConfigJson);
        if (!(parsed instanceof Map)) {
            return true;
        }

        Map<String, Object> config = (Map<String, Object>) parsed;

        // 检查 fieldConditions：指定哪些字段变更时才触发
        Object fieldConditions = config.get("fieldConditions");
        if (fieldConditions instanceof List<?> conditions && !conditions.isEmpty()) {
            if (changedFields == null || changedFields.isEmpty()) {
                return false;
            }
            for (Object cond : conditions) {
                if (cond instanceof Map<?, ?> condition) {
                    String fieldIdStr = String.valueOf(condition.get("fieldId"));
                    if (changedFields.containsKey(fieldIdStr)) {
                        return true;
                    }
                }
            }
            return false; // 有字段条件但都不匹配
        }

        return true;
    }

    private String buildEventId(Long automationId, Long tableId, Long recordId, String changeType) {
        return "auto_" + automationId + "_" + tableId + "_" + recordId + "_" + changeType + "_" + System.currentTimeMillis();
    }

    private void markRunFailed(Long runId, String errorCode, String errorMessage) {
        UpdateWrapper<BitableAutomationRun> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", runId)
                .set("status", "failed")
                .set("error_code", errorCode)
                .set("error_message", errorMessage != null && errorMessage.length() > 2000
                        ? errorMessage.substring(0, 2000) : errorMessage)
                .set("finished_at", LocalDateTime.now());
        runMapper.update(null, wrapper);
    }

    private BitableAutomationVO toVO(BitableAutomation automation) {
        BitableAutomationVO vo = new BitableAutomationVO();
        vo.setId(automation.getId());
        vo.setBaseId(automation.getBaseId());
        vo.setTableId(automation.getTableId());
        vo.setName(automation.getName());
        vo.setStatus(automation.getStatus());
        vo.setTriggerType(automation.getTriggerType());
        vo.setTriggerConfig(BitableJsonUtils.parseJson(automation.getTriggerConfig()));
        vo.setActionType(automation.getActionType());
        vo.setActionConfig(BitableJsonUtils.parseJson(automation.getActionConfig()));
        vo.setCreatedBy(automation.getCreatedBy());
        vo.setCreatedAt(automation.getCreatedAt() != null
                ? automation.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null);
        vo.setUpdatedAt(automation.getUpdatedAt() != null
                ? automation.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null);
        return vo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Collections.emptyMap();
        }
        Object parsed = BitableJsonUtils.parseJson(configJson);
        return parsed instanceof Map ? (Map<String, Object>) parsed : Collections.emptyMap();
    }

    private Long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String replaceContextVariables(String template, Map<String, Object> context) {
        if (template == null) return null;
        String result = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    private void validateTriggerType(String triggerType) {
        Set<String> validTypes = Set.of("record_created", "record_updated", "record_deleted", "form_submitted", "scheduled");
        if (!validTypes.contains(triggerType)) {
            throw new BusinessException("不支持的触发器类型: " + triggerType);
        }
    }

    private void validateActionType(String actionType) {
        Set<String> validTypes = Set.of("update_record", "create_record", "send_message", "http_request");
        if (!validTypes.contains(actionType)) {
            throw new BusinessException("不支持的动作类型: " + actionType);
        }
    }
}
