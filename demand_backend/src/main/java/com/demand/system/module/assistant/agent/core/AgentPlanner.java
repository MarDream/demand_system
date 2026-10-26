package com.demand.system.module.assistant.agent.core;

import com.demand.system.module.assistant.agent.dto.AgentPlan;
import com.demand.system.module.assistant.agent.dto.AgentSubTask;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 任务规划器：把复杂问题拆分为可并行执行的子任务（每个子任务交给一个子 Agent）。
 * <p>输出严格 JSON；解析失败或未配置规划模型时退化为单任务（由主 Agent 直接执行）。</p>
 */
@Component
public class AgentPlanner {

    private static final Logger log = LoggerFactory.getLogger(AgentPlanner.class);

    /** 子任务类型白名单（决定子 Agent 的工具集与模型功能点） */
    static final Set<String> SUBTASK_TYPES = Set.of("research", "data_analysis", "navigation", "general");
    /** 子任务数量上限 */
    private static final int MAX_SUBTASKS = 4;

    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    public AgentPlanner(LlmGateway llmGateway, ObjectMapper objectMapper) {
        this.llmGateway = llmGateway;
        this.objectMapper = objectMapper;
    }

    /**
     * @param userMessage 用户原始问题
     * @param provider    规划模型（由编排器解析，含 planner 功能点回退）
     * @return 拆分计划；任何异常退化为单任务
     */
    public AgentPlan plan(String userMessage,
                          com.demand.system.module.assistant.dto.AssistantPageContext pageContext,
                          LlmGatewayConfig.Provider provider,
                          BigDecimal temperature,
                          Integer maxTokens) {
        if (provider == null) {
            return new AgentPlan(AgentSubTask.single(userMessage));
        }
        try {
            String systemPrompt = "你是任务规划器。把用户的复杂问题拆分成 1~4 个可独立执行的子任务，"
                    + "交给不同的子智能体并行处理。只输出 JSON，不要输出任何其它文字。"
                    + "子任务类型只能取以下值：\n"
                    + "- research：检索知识库文档、工单正文或联网搜索等资料调研\n"
                    + "- data_analysis：查询业务数据库、统计汇总数据\n"
                    + "- navigation：查找系统内页面入口与操作指引\n"
                    + "- general：其它通用推理任务（含报告/总结/方案的撰写产出）\n"
                    + "输出格式：{\"subtasks\":[{\"type\":\"...\",\"title\":\"简短中文标题\",\"instruction\":\"完整的执行指令，包含问题原文与必要上下文\"}]}\n"
                    + "要求：\n"
                    + "1. 子任务之间相互独立可并行；instruction 必须自包含（子智能体看不到用户对话历史）。\n"
                    + "2. 产出型请求（输出报告/生成总结/分析）必须拆出收集数据的子任务"
                    + "（data_analysis 查业务数据、research 查资料），并拆一个 general 任务负责汇总成文。\n"
                    + "3. 用户任务的关键信息严重缺失且无法推断时（如报告范围/口径/格式不明），"
                    + "拆成 1 个 general 任务，并在 instruction 开头注明「需先向用户澄清关键信息后再执行」。"
                    + "简单问题拆成 1 个 general 任务即可。";
            StringBuilder user = new StringBuilder("用户问题：").append(userMessage);
            if (pageContext != null && pageContext.getPageTitle() != null) {
                user.append("\n当前页面：").append(pageContext.getPageTitle());
            }
            LlmGateway.ChatResult result = llmGateway.chatWithProvider(
                    provider, systemPrompt, user.toString(), temperature, maxTokens);
            return parsePlan(result.getContent(), userMessage);
        } catch (Exception e) {
            log.warn("agent planner failed, fallback to single task", e);
            return new AgentPlan(AgentSubTask.single(userMessage));
        }
    }

    AgentPlan parsePlan(String content, String userMessage) {
        if (content == null || content.isBlank()) {
            return new AgentPlan(AgentSubTask.single(userMessage));
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            JsonNode subtasksNode = root.path("subtasks");
            if (!subtasksNode.isArray() || subtasksNode.isEmpty()) {
                return new AgentPlan(AgentSubTask.single(userMessage));
            }
            List<AgentSubTask> subtasks = new ArrayList<>();
            for (JsonNode node : subtasksNode) {
                if (subtasks.size() >= MAX_SUBTASKS) {
                    break;
                }
                String type = normalizeType(node.path("type").asText("general"));
                String title = node.path("title").asText("");
                String instruction = node.path("instruction").asText("");
                if (instruction.isBlank()) {
                    continue;
                }
                if (title.isBlank()) {
                    title = "子任务 " + (subtasks.size() + 1);
                }
                subtasks.add(new AgentSubTask(type, title, instruction));
            }
            if (subtasks.isEmpty()) {
                return new AgentPlan(AgentSubTask.single(userMessage));
            }
            return new AgentPlan(subtasks);
        } catch (Exception e) {
            log.warn("agent plan json parse failed, fallback to single task, content={}", content, e);
            return new AgentPlan(AgentSubTask.single(userMessage));
        }
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "general";
        }
        String normalized = type.trim().toLowerCase();
        return SUBTASK_TYPES.contains(normalized) ? normalized : "general";
    }

    /** 从模型输出中提取 JSON 主体（容忍 markdown 代码块包裹与前后杂文） */
    static String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("输出中未找到 JSON 对象");
        }
        return content.substring(start, end + 1);
    }
}
