package com.demand.system.module.assistant.agent.core;

import com.demand.system.module.assistant.agent.dto.AgentSubTask;
import com.demand.system.module.assistant.agent.dto.SubAgentRun;
import com.demand.system.module.assistant.agent.tools.AgentToolCatalog;
import com.demand.system.module.assistant.dto.AssistantTask;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 子 Agent 执行器：按子任务类型组装角色提示词与工具集，跑一轮工具循环，产出简报。
 * 单 Agent 路径同样复用本类（agentId="main"）。
 */
@Component
public class SubAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(SubAgentRunner.class);

    /** 执行模型（provider + 采样参数 + 展示名） */
    public record SubAgentModel(
            LlmGatewayConfig.Provider provider,
            BigDecimal temperature,
            Integer maxTokens,
            String label) {}

    public record SubAgentOutcome(SubAgentRun run, AgentLoop.LoopResult loopResult) {}

    private final AgentLoop agentLoop;
    private final AgentToolCatalog catalog;

    public SubAgentRunner(AgentLoop agentLoop, AgentToolCatalog catalog) {
        this.agentLoop = agentLoop;
        this.catalog = catalog;
    }

    public SubAgentOutcome run(AgentSubTask subTask,
                               String agentId,
                               SubAgentModel model,
                               AgentToolContext context,
                               Long sessionId,
                               AgentEventSink sink) {
        SubAgentRun run = new SubAgentRun(agentId, subTask.getType(), subTask.getTitle(), subTask.getInstruction());
        run.setModelLabel(model.label());
        run.setStatus("running");
        run.setStartedAt(Instant.now().toEpochMilli());
        sink.onSubAgent(run);

        AssistantTask task = new AssistantTask(agentId, "[" + typeLabel(subTask.getType()) + "] " + subTask.getTitle());
        task.start("模型 " + model.label());
        sink.onTask(task);

        try {
            // 主智能体（agentId=main）可向用户提问澄清；子 Agent 后台执行无人应答，不给 ask_user
            List<com.demand.system.module.assistant.agent.core.AgentTool> tools = "main".equals(agentId)
                    ? catalog.mainLoopTools()
                    : catalog.forSubTaskType(subTask.getType());
            AgentLoop.LoopResult loopResult = agentLoop.run(new AgentLoop.LoopRequest(
                    model.provider(),
                    model.temperature(),
                    model.maxTokens(),
                    systemPromptFor(subTask.getType(), "main".equals(agentId)),
                    subTask.getInstruction(),
                    tools,
                    context,
                    agentId,
                    sessionId,
                    new AgentLoop.LoopListener() {
                        @Override
                        public void onToolCall(com.demand.system.module.assistant.agent.dto.ToolCallRecord record) {
                            sink.onToolCall(record);
                        }

                        @Override
                        public void onPermissionRequest(String requestId,
                                                        com.demand.system.module.assistant.agent.dto.ToolCallRecord record) {
                            sink.onPermissionRequest(requestId, record);
                        }
                    }));
            run.setToolCalls(loopResult.getToolCalls());
            String report = loopResult.getDirectAnswer() == null ? "" : loopResult.getDirectAnswer();
            run.setSummary(report);
            run.setStatus("completed");
            run.setCompletedAt(Instant.now().toEpochMilli());
            int toolCount = loopResult.getToolCalls().size();
            task.log("info", "工具调用 " + toolCount + " 次，轮数 " + loopResult.getRounds());
            task.complete("产出简报 " + report.length() + " 字");
            sink.onTask(task);
            sink.onSubAgent(run);
            return new SubAgentOutcome(run, loopResult);
        } catch (Exception e) {
            log.warn("sub agent failed, agentId={}, type={}", agentId, subTask.getType(), e);
            run.setStatus("failed");
            run.setSummary("子智能体执行失败：" + (e.getMessage() != null ? e.getMessage() : "未知原因"));
            run.setCompletedAt(Instant.now().toEpochMilli());
            task.fail("执行失败：" + (e.getMessage() != null ? e.getMessage() : "未知原因"));
            sink.onTask(task);
            sink.onSubAgent(run);
            return new SubAgentOutcome(run, null);
        }
    }

    private String typeLabel(String type) {
        return switch (type == null ? "general" : type) {
            case "research" -> "检索调研";
            case "data_analysis" -> "数据分析";
            case "navigation" -> "操作导航";
            default -> "通用执行";
        };
    }

    private String systemPromptFor(String type, boolean isMainAgent) {
        return switch (type == null ? "general" : type) {
            case "research" -> "你是资料调研子智能体，负责检索企业知识库、工单正文与互联网资料。\n"
                    + "必须先调用工具获取事实再下结论，禁止编造。\n"
                    + "完成后输出最终简报：结论先行，逐条列出关键事实，每个事实注明来源（文档名/工单编号/网站）。";
            case "data_analysis" -> "你是数据分析子智能体，负责把自然语言转成只读 SQL 查询业务数据库。\n"
                    + "所有数字必须来自查询结果并原样引用，禁止编造、估算或推算。\n"
                    + "完成后输出最终简报：数字结论先行，附关键数据（表格形式），注明统计口径。";
            case "navigation" -> "你是操作导航子智能体，负责查找系统内页面入口与操作路径。\n"
                    + "必须基于 navigate 工具返回的入口作答，不得虚构菜单。\n"
                    + "完成后输出最终简报：入口路径与操作步骤。";
            default -> isMainAgent
                    ? "你是企业需求系统的主智能体，直接替用户完成任务。\n"
                            + "核心原则：\n"
                            + "1. 用户让你「输出报告/生成总结/分析/整理资料」时，你要亲自调用工具收集数据"
                            + "（data_query 查业务数据、knowledge_search/requirement_search 查资料、web_search 查外部信息），"
                            + "然后基于收集到的事实直接产出最终内容——而不是告诉用户去哪个页面手动操作。\n"
                            + "2. 只有当用户的问题本身就是「怎么操作/在哪配置」时，才给出操作指引。\n"
                            + "3. 任务的关键信息缺失且无法合理推断时（如报告口径/范围/格式、需求归属项目），"
                            + "调用 ask_user 工具向用户提出具体、可一次性回答的澄清问题，不要擅自假设。\n"
                            + "4. 所有数字与事实必须来自工具结果，禁止编造；注明数据来源与统计口径。\n"
                            + "5. 最终输出结构化、可直接使用的内容（报告要分章节、数据要成表格）。"
                    : "你是通用执行子智能体，可按需调用提供的工具完成任务。\n"
                            + "基于事实作答；能用工具核实的就先核实，禁止编造。\n"
                            + "完成后输出最终简报：结论先行，注明事实来源。";
        };
    }
}
