package com.demand.system.module.assistant.agent.core;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 智能体智能路由（纯规则，不耗 LLM）：
 * 判断用户问题走原通用链路还是进入智能体编排。
 * <ul>
 *   <li>agentMode=off：强制原链路</li>
 *   <li>agentMode=on：强制智能体编排</li>
 *   <li>auto（默认）：命中复杂问题特征才编排——多意图连接词、查数+查资料组合、
 *       对比/分别汇总类、或问题较长且包含明确的数据/资料诉求</li>
 * </ul>
 */
@Component
public class AgentModeRouter {

    public enum Decision {
        /** 原通用链路（规则导航 + 跨源摘要） */
        LEGACY,
        /** 智能体编排（规划 → 子Agent → 综合） */
        AGENT
    }

    private static final Pattern MULTI_INTENT_PATTERN = Pattern.compile(
            "并且|同时|分别|然后|以及|再|还要|顺便|对比一下|对比下");
    private static final Pattern DATA_INTENT_PATTERN = Pattern.compile(
            "多少|几条|几个|统计|汇总|数量|分布|占比|比例|平均|排名|排行|趋势|列表|清单|清单");
    private static final Pattern DOC_INTENT_PATTERN = Pattern.compile(
            "检索|查找|搜索|规范|文档|手册|制度|流程是什么|怎么操作|有没有.*资料|类似");
    private static final Pattern COMPLEX_HINT_PATTERN = Pattern.compile(
            "对比|分别列出|汇总|整理.*并|帮我.*并且|综合分析");
    /**
     * 产出型任务：用户要的是智能体直接产出内容（报告/分析/方案/总结），
     * 需要工具收集数据后综合成稿，绝不是"教用户去哪里手动操作"。
     */
    private static final Pattern OUTPUT_INTENT_PATTERN = Pattern.compile(
            "输出.*报告|生成.*报告|写.*报告|出.*报告|输出.*总结|生成.*总结|写.*总结|做.*总结|"
                    + "输出.*分析|生成.*分析|写.*分析|出.*分析|输出.*方案|生成.*方案|写.*方案|"
                    + "整理.*报告|整理.*资料|汇编|一份.*报告|一份.*总结|一份.*分析|一份.*方案|一份.*清单|"
                    + "帮我.*(总结|汇报|盘点|梳理)|总结一下.*(情况|进展|工作)");

    /** @param agentMode 请求参数：off / on / auto（null 视为 auto） */
    public Decision route(String agentMode, String userContent) {
        if ("off".equalsIgnoreCase(agentMode)) {
            return Decision.LEGACY;
        }
        if ("on".equalsIgnoreCase(agentMode)) {
            return Decision.AGENT;
        }
        return shouldOrchestrate(userContent) ? Decision.AGENT : Decision.LEGACY;
    }

    /** 路由结论的原因描述（写入轨迹与任务日志） */
    public String describe(Decision decision, String userContent) {
        if (decision == Decision.LEGACY) {
            return "问题特征简单，走标准链路";
        }
        if (OUTPUT_INTENT_PATTERN.matcher(userContent).find()) {
            return "产出型任务（报告/总结/分析），进入智能体编排";
        }
        if (MULTI_INTENT_PATTERN.matcher(userContent).find()) {
            return "检测到多意图问题，进入智能体编排";
        }
        if (DATA_INTENT_PATTERN.matcher(userContent).find()
                && DOC_INTENT_PATTERN.matcher(userContent).find()) {
            return "同时需要查数据与查资料，进入智能体编排";
        }
        if (COMPLEX_HINT_PATTERN.matcher(userContent).find()) {
            return "复杂汇总/对比类问题，进入智能体编排";
        }
        return "问题较长且包含明确诉求，进入智能体编排";
    }

    private boolean shouldOrchestrate(String userContent) {
        if (userContent == null || userContent.isBlank()) {
            return false;
        }
        String content = userContent.trim();
        // 产出型任务优先：报告/总结/分析/方案必须由智能体直接产出
        if (OUTPUT_INTENT_PATTERN.matcher(content).find()) {
            return true;
        }
        boolean multiIntent = MULTI_INTENT_PATTERN.matcher(content).find();
        boolean dataAndDoc = DATA_INTENT_PATTERN.matcher(content).find()
                && DOC_INTENT_PATTERN.matcher(content).find();
        boolean complexHint = COMPLEX_HINT_PATTERN.matcher(content).find();
        if (multiIntent || dataAndDoc || complexHint) {
            return true;
        }
        // 长问题且带有数据/资料诉求词，大概率需要多步
        boolean hasIntent = DATA_INTENT_PATTERN.matcher(content).find()
                || DOC_INTENT_PATTERN.matcher(content).find();
        return hasIntent && content.length() >= 30;
    }
}
