package com.demand.system.module.bitable.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多维表格-自动化 MQ 配置
 */
@Configuration
public class BitableAutomationMQConfig {

    public static final String AUTOMATION_QUEUE = "bitable.automation.execute";
    public static final String AUTOMATION_EXCHANGE = "bitable.automation";
    public static final String AUTOMATION_ROUTING_KEY = "automation.execute";

    @Bean
    public Queue automationQueue() {
        return QueueBuilder.durable(AUTOMATION_QUEUE).build();
    }

    @Bean
    public DirectExchange automationExchange() {
        return new DirectExchange(AUTOMATION_EXCHANGE);
    }

    @Bean
    public Binding automationBinding(Queue automationQueue, DirectExchange automationExchange) {
        return BindingBuilder.bind(automationQueue).to(automationExchange).with(AUTOMATION_ROUTING_KEY);
    }
}
