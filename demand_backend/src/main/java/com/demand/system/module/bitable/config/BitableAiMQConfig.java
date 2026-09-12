package com.demand.system.module.bitable.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多维表格-AI 异步任务 MQ 配置
 * <p>
 * AI 批量填充走默认交换机按队列名直投（convertAndSend(queueName, message)），
 * 此处仅声明持久化队列，确保消息在无消费者期间不丢失。
 */
@Configuration
public class BitableAiMQConfig {

    public static final String AI_FILL_BATCH_QUEUE = "bitable.ai.fill.batch";

    @Bean
    public Queue aiFillBatchQueue() {
        return QueueBuilder.durable(AI_FILL_BATCH_QUEUE).build();
    }
}
