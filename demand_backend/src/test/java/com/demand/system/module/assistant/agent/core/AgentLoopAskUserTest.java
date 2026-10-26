package com.demand.system.module.assistant.agent.core;

import com.demand.system.module.knowledge.llm.AgentChatMessage;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.llm.ToolChatResult;
import com.demand.system.module.knowledge.llm.ToolCallPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** ask_user 澄清工具：模型发起提问时循环以问题为直答终止 */
class AgentLoopAskUserTest {

    private LlmGateway llmGateway;
    private AgentLoop loop;
    private ToolChatResult askUserResponse;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        llmGateway = mock(LlmGateway.class);
        askUserResponse = new ToolChatResult();
        askUserResponse.setToolCalls(List.of(new ToolCallPayload("call-1", "ask_user",
                "{\"question\":\"报告需要覆盖哪些范围？\"}")));
        when(llmGateway.chatWithTools(any(LlmGatewayConfig.Provider.class), anyString(),
                anyList(), anyList(), any(BigDecimal.class), any(Integer.class)))
                .thenReturn(askUserResponse);
        loop = new AgentLoop(llmGateway, mock(AgentConfirmationRegistry.class),
                new ObjectMapper(), Runnable::run);
    }

    @Test
    void askUserToolTerminatesLoopWithQuestionAsAnswer() {
        AgentToolContext context = new AgentToolContext(
                1L, false, List.of(), null, null, null, null);
        AgentLoop.LoopResult result = loop.run(new AgentLoop.LoopRequest(
                new LlmGatewayConfig.Provider(),
                BigDecimal.ONE,
                2048,
                "system",
                "输出一份报告",
                List.of(new com.demand.system.module.assistant.agent.tools.AskUserTool()),
                context,
                "main",
                100L,
                new AgentLoop.LoopListener() {}));

        assertEquals("报告需要覆盖哪些范围？", result.getDirectAnswer());
        assertEquals(1, result.getToolCalls().size());
        assertEquals("ask_user", result.getToolCalls().get(0).getToolName());
        assertEquals("completed", result.getToolCalls().get(0).getStatus());
        // 只调了一轮就终止，没有继续追问模型
        verifySingleRound();
    }

    @SuppressWarnings("unchecked")
    private void verifySingleRound() {
        // chatWithTools 仅被调用一次（mock 计数由 when(...).thenReturn 覆盖即可：再验证消息列表无 tool 结果回传）
        org.mockito.Mockito.verify(llmGateway, org.mockito.Mockito.times(1))
                .chatWithTools(any(LlmGatewayConfig.Provider.class), anyString(),
                        anyList(), anyList(), any(BigDecimal.class), any(Integer.class));
    }
}
