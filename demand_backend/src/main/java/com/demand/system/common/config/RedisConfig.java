package com.demand.system.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 配置类
 *
 * 功能：
 * 1. 配置 RedisTemplate 用于直接操作 Redis
 * 2. 配置 RedisCacheManager 用于 Spring Cache 注解（@Cacheable 等）
 * 3. 启用缓存功能（@EnableCaching）
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * 构建支持 Java 8 时间类型（LocalDateTime 等）并保留类型信息的 ObjectMapper
     *
     * 解决 Redis 序列化报错：
     * "Java 8 date/time type `java.time.LocalDateTime` not supported by default"
     *
     * 关键配置：
     * - 注册 JavaTimeModule：支持 LocalDateTime / LocalDate 等 Java 8 时间类型
     * - 禁用 WRITE_DATES_AS_TIMESTAMPS：时间序列化为 ISO-8601 字符串而非数值时间戳
     * - 开启默认类型信息：反序列化时能还原为具体类型（User / SysOrgVO 等），
     *   使 {@code instanceof} 检查正常工作
     */
    private ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    /**
     * 构建带类型信息的 JSON 序列化器（RedisTemplate 与 CacheManager 共用）
     */
    private GenericJackson2JsonRedisSerializer buildJsonSerializer() {
        return new GenericJackson2JsonRedisSerializer(buildObjectMapper());
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer jsonSerializer = buildJsonSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置 Spring Cache 使用的 RedisCacheManager
     *
     * 性能优化：为 @Cacheable 注解提供 Redis 缓存支持
     * - 默认 TTL: 5分钟（application.yml 中配置）
     * - Key 前缀: "demand:"
     * - 不缓存 null 值
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))  // 默认 TTL 5分钟
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(buildJsonSerializer()))
                .disableCachingNullValues();  // 不缓存 null 值

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
