package com.demand.system.module.bitable.listener;

import com.demand.system.module.bitable.service.BitableAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 多维表格-AI 异步任务 MQ 消费者
 */
@Component
public class BitableAiListener {

    private static final Logger log = LoggerFactory.getLogger(BitableAiListener.class);

    private final BitableAiService bitableAiService;

    public BitableAiListener(BitableAiService bitableAiService) {
        this.bitableAiService = bitableAiService;
    }

    /**
     * 消费 AI 批量填充任务（队列由 BitableAiMQConfig 声明）
     */
    @RabbitListener(queues = "bitable.ai.fill.batch")
    public void onFillBatchMessage(Map<String, Object> message) {
        Long tableId = message.get("tableId") != null ? Long.valueOf(message.get("tableId").toString()) : null;
        Long fieldId = message.get("fieldId") != null ? Long.valueOf(message.get("fieldId").toString()) : null;
        Long userId = message.get("userId") != null ? Long.valueOf(message.get("userId").toString()) : null;

        if (tableId == null || fieldId == null) {
            log.warn("AI批量填充消息缺少 tableId/fieldId，丢弃: {}", message);
            return;
        }

        log.info("收到AI批量填充任务: tableId={}, fieldId={}, userId={}", tableId, fieldId, userId);
        try {
            bitableAiService.processFillBatch(tableId, fieldId, userId);
        } catch (Exception e) {
            log.error("AI批量填充任务执行失败: tableId={}, fieldId={}", tableId, fieldId, e);
        }
    }
}
