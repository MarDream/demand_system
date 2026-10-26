package com.demand.system.module.assistant.agent.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 智能路由规则：off/on 强制；auto 按问题特征判定 */
class AgentModeRouterTest {

    private final AgentModeRouter router = new AgentModeRouter();

    @Test
    void explicitModeWins() {
        assertEquals(AgentModeRouter.Decision.LEGACY, router.route("off", "统计一下本月工单数量并且检索相关规范"));
        assertEquals(AgentModeRouter.Decision.AGENT, router.route("on", "怎么新建需求"));
    }

    @Test
    void autoRoutesSimpleQuestionToLegacy() {
        assertEquals(AgentModeRouter.Decision.LEGACY, router.route(null, "怎么新建需求"));
        assertEquals(AgentModeRouter.Decision.LEGACY, router.route("auto", "在哪配置工作流"));
    }

    @Test
    void autoRoutesMultiIntentToAgent() {
        assertEquals(AgentModeRouter.Decision.AGENT, router.route(null, "统计一下本月工单数量，并且帮我检索上线规范文档"));
    }

    @Test
    void autoRoutesDataPlusDocToAgent() {
        assertEquals(AgentModeRouter.Decision.AGENT, router.route("auto", "查一下本月工单的数量分布，再看看相关制度文档"));
    }

    @Test
    void autoRoutesLongDataQuestionToAgent() {
        assertEquals(AgentModeRouter.Decision.AGENT, router.route(null,
                "帮我统计一下当前迭代里所有高优先级需求的数量和完成情况汇总"));
    }

    @Test
    void autoRoutesOutputTaskToAgent() {
        // 产出型任务：报告/总结/分析必须由智能体直接产出，不能退化为操作导航
        assertEquals(AgentModeRouter.Decision.AGENT, router.route(null,
                "输出一份2026年关于i莞家小程序的功能建设相关报告"));
        assertEquals(AgentModeRouter.Decision.AGENT, router.route(null,
                "帮我生成一份本月需求交付情况总结"));
        assertEquals(AgentModeRouter.Decision.AGENT, router.route(null,
                "整理一份测试方案"));
    }
}
