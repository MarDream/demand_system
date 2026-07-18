package com.demand.system.module.bitable.listener;

import com.demand.system.module.bitable.service.BitableAutomationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 多维表格-自动化 MQ 消费者
 */
@Component
public class BitableAutomationListener {

    private static final Logger log = LoggerFactory.getLogger(BitableAutomationListener.class);

    private final BitableAutomationService automationService;

    public BitableAutomationListener(BitableAutomationService automationService) {
        this.automationService = automationService;
    }

    @RabbitListener(queues = "bitable.automation.execute")
    public void onAutomationMessage(Map<String, Object> message) {
        Long automationId = Long.valueOf(message.get("automationId").toString());
        Long runId = Long.valueOf(message.get("runId").toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) message.get("context");

        log.info("收到自动化MQ消息: automationId={}, runId={}", automationId, runId);

        try {
            automationService.executeAutomation(automationId, runId, context);
        } catch (Exception e) {
            log.error("自动化执行异常: automationId={}, runId={}", automationId, runId, e);
            // 执行失败已在 service 内部处理（更新 run 状态为 failed）
        }
    }
}
