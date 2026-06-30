package com.demand.system.common.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "notification.exchange";
    public static final String QUEUE_NAME = "notification.system.queue";
    public static final String ROUTING_KEY = "notification.#";

    public static final String KNOWLEDGE_EXCHANGE = "knowledge.exchange";
    public static final String KNOWLEDGE_DOC_PROCESS_QUEUE = "knowledge.document.process.queue";
    public static final String KNOWLEDGE_DOC_PROCESS_KEY = "knowledge.document.process";

    public static final String KNOWLEDGE_DLX_EXCHANGE = "knowledge.dlx.exchange";
    public static final String KNOWLEDGE_DLQ_QUEUE = "knowledge.document.process.dlq";
    public static final String KNOWLEDGE_DLQ_ROUTING_KEY = "knowledge.document.process.dead";

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(ROUTING_KEY);
    }

    @Bean
    public TopicExchange knowledgeExchange() {
        return new TopicExchange(KNOWLEDGE_EXCHANGE, true, false);
    }

    @Bean
    public Queue knowledgeDocProcessQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", KNOWLEDGE_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", KNOWLEDGE_DLQ_ROUTING_KEY);
        return new Queue(KNOWLEDGE_DOC_PROCESS_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding knowledgeDocProcessBinding(Queue knowledgeDocProcessQueue, TopicExchange knowledgeExchange) {
        return BindingBuilder.bind(knowledgeDocProcessQueue).to(knowledgeExchange).with(KNOWLEDGE_DOC_PROCESS_KEY);
    }

    @Bean
    public DirectExchange knowledgeDlxExchange() {
        return new DirectExchange(KNOWLEDGE_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue knowledgeDlqQueue() {
        return new Queue(KNOWLEDGE_DLQ_QUEUE, true);
    }

    @Bean
    public Binding knowledgeDlqBinding(Queue knowledgeDlqQueue, DirectExchange knowledgeDlxExchange) {
        return BindingBuilder.bind(knowledgeDlqQueue).to(knowledgeDlxExchange).with(KNOWLEDGE_DLQ_ROUTING_KEY);
    }
}
