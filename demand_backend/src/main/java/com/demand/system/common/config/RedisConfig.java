package com.demand.system.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
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
     * 构建带类型信息的 JSON 序列化器（RedisTemplate 与 CacheManager 共用）
     *
     * 使用 Spring Data Redis 4.0+ 推荐的 GenericJacksonJsonRedisSerializer（基于 Jackson 3.x），
     * 替代已废弃的 GenericJackson2JsonRedisSerializer（基于 Jackson 2.x，标记为待删除）。
     *
     * 配置说明：
     * - enableUnsafeDefaultTyping：开启默认类型信息，反序列化时能还原为具体类型（User / SysOrgVO 等）
     * - enableSpringCacheNullValueSupport：支持 Spring Cache 的 NullValue 序列化
     *
     * 注意：Jackson 3.x 内置 JSR-310 时间类型支持，默认 ISO-8601 格式输出，
     * 无需额外注册 JavaTimeModule 或禁用 WRITE_DATES_AS_TIMESTAMPS（该 Feature 已移除）
     */
    private GenericJacksonJsonRedisSerializer buildJsonSerializer() {
        return GenericJacksonJsonRedisSerializer.create(builder -> builder
                .enableUnsafeDefaultTyping()
                .enableSpringCacheNullValueSupport()
        );
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        GenericJacksonJsonRedisSerializer jsonSerializer = buildJsonSerializer();
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

    /**
     * 配置 RedisMessageListenerContainer
     * <p>
     * 用于订阅 Redis pub/sub channel，转发消息到 WebSocket sessions。
     *
     * 说明：默认的 LettuceConnectionFactory 开启 shareNativeConnection=true，
     * 阻塞式订阅（psubscribe）会占用那条共享原生连接，导致 RedisTemplate 普通命令拿不到连接、
     * 后端表现像"假死"。已在 application-dev.yml 中设置
     * spring.data.redis.lettuce.share-native-connection=false 关闭原生连接共享，
     * 使订阅与普通命令在连接层面隔离。
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
