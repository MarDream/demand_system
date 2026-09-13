package com.demand.system.common.util;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * JSON 层接受 true/false 或 0/1，实体属性保持 Integer 以映射数据库 tinyint。
 * <p>Spring Boot 4 的 HTTP 消息转换使用 Jackson 3（tools.jackson），其默认不再把
 * Boolean 强转为 Integer（报 Cannot deserialize value of type `java.lang.Integer` from
 * Boolean value），而前端 API 契约以 boolean 表达 required/enabled/isAiField 等标志位。
 * 经实测，withCoercionConfig(Boolean→TryConvert) 对 Integer 反序列化路径不生效，
 * 必须以 SimpleModule.addDeserializer 方式替换 Integer 反序列化器（见 JacksonConfig）。
 */
public class LenientBooleanIntegerDeserializer extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt) {
        return switch (p.currentToken()) {
            case VALUE_TRUE -> 1;
            case VALUE_FALSE -> 0;
            case VALUE_NUMBER_INT -> p.getIntValue();
            case VALUE_STRING -> {
                String s = p.getText().trim();
                if (s.isEmpty() || "null".equalsIgnoreCase(s)) yield null;
                if ("true".equalsIgnoreCase(s)) yield 1;
                if ("false".equalsIgnoreCase(s)) yield 0;
                yield Integer.valueOf(s);
            }
            default -> (Integer) ctxt.handleUnexpectedToken(Integer.class, p);
        };
    }
}
