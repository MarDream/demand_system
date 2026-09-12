package com.demand.system.module.bitable.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 统一 JSON 消息转换器。
 * <p>
 * Spring Boot 4（spring-amqp 4.x）下默认的 SimpleMessageConverter 走 Java 序列化，
 * 监听端按方法签名推断类型转换失败（ListenerExecutionFailedException: Failed to convert message），
 * 自动化消息被拒绝后永久 pending。显式声明 Jackson JSON 转换器后，
 * Boot 自动配置会将其同时应用到 RabbitTemplate 与监听容器工厂，
 * 生产/消费两侧统一为 application/json。
 */
@Configuration
public class RabbitMessageConverterConfig {

    @Bean
    public MessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
