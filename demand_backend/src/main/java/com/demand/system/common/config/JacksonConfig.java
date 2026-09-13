package com.demand.system.common.config;

import com.demand.system.common.util.LenientBooleanIntegerDeserializer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 全局宽松化：允许 JSON true/false 反序列化为 Integer/int（1/0）。
 * <p>Spring Boot 4 的 HTTP 消息转换使用 Jackson 3，其默认不再做 Boolean→Integer 强转
 * （报 Cannot deserialize value of type `java.lang.Integer` from Boolean value），
 * 而本系统前端 API 契约以 boolean 表达数据库 tinyint 标志位（required/enabled/isAiField 等）。
 * 注意：com.fasterxml 的 @JsonDeserialize 注解与 withCoercionConfig(Boolean→TryConvert)
 * 在 Jackson 3 的这条路径上均不生效（前者注解体系不同，后者经实测未生效），
 * 必须以 SimpleModule 全局替换 Integer 反序列化器。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer lenientBooleanIntegerCustomizer() {
        SimpleModule module = new SimpleModule("lenient-boolean-integer");
        module.addDeserializer(Integer.class, new LenientBooleanIntegerDeserializer());
        return builder -> builder.addModule(module);
    }
}
