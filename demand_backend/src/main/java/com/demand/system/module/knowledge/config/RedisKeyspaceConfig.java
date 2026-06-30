package com.demand.system.module.knowledge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import com.demand.system.module.knowledge.consumer.DocumentTimeoutKeyListener;

/**
 * Redis Keyspace Notification 配置
 *
 * 功能：启用 Redis 过期事件监听，用于文档处理超时检测。
 * 当带 TTL 的 Redis key 过期时，Redis 会发布 __keyevent@0__:expired 事件，
 * 本配置注册监听器接收该事件，实现零轮询的超时检测机制。
 *
 * 注意：应用启动时会尝试通过 CONFIG SET 设置 notify-keyspace-events Ex，
 * 若 Redis 服务端禁用了 CONFIG 命令（如云托管 Redis），需手动在 Redis 配置中开启。
 */
@Configuration
public class RedisKeyspaceConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisKeyspaceConfig.class);

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            DocumentTimeoutKeyListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic("__keyevent@0__:expired"));
        return container;
    }

    /**
     * 应用启动后确保 Redis 开启了过期事件通知。
     * 通过 ApplicationRunner 在 Bean 初始化完成后执行。
     */
    @Bean
    public org.springframework.boot.ApplicationRunner enableKeyspaceNotifications(RedisTemplate<String, Object> redisTemplate) {
        return args -> {
            try {
                redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                    connection.serverCommands().setConfig("notify-keyspace-events", "Ex");
                    return null;
                });
                log.info("已启用 Redis keyspace 过期事件通知 (notify-keyspace-events=Ex)");
            } catch (Exception e) {
                log.warn("无法自动设置 Redis notify-keyspace-events，请手动配置: {}", e.getMessage());
            }
        };
    }
}
