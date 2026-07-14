package com.demand.system.module.bitable.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 多维表格 Redis 订阅器
 * <p>
 * 订阅 Redis channel: bitable:update:{baseId}，收到消息后转发给本节点的 WebSocket sessions。
 * 使用 psubscribe 模式匹配 bitable:update:* 来监听所有 baseId 的更新。
 */
@Component
public class BitableRedisSubscriber {

    private static final Logger log = LoggerFactory.getLogger(BitableRedisSubscriber.class);

    private static final String CHANNEL_PATTERN = "bitable:update:*";

    private final RedisTemplate<String, Object> redisTemplate;
    private final BitableWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer listenerContainer;

    public BitableRedisSubscriber(RedisTemplate<String, Object> redisTemplate,
                                  BitableWebSocketHandler webSocketHandler,
                                  ObjectMapper objectMapper,
                                  @Qualifier("redisMessageListenerContainer") RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
        this.listenerContainer = listenerContainer;
    }

    @PostConstruct
    public void subscribe() {
        MessageListener listener = (message, pattern) -> {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            handleRedisMessage(channel, body);
        };
        listenerContainer.addMessageListener(listener, new PatternTopic(CHANNEL_PATTERN));
        log.info("Redis 订阅器已启动，监听 channel 模式: {}", CHANNEL_PATTERN);
    }

    /**
     * 处理 Redis pub/sub 消息，转发给对应 baseId 的 WebSocket sessions
     */
    private void handleRedisMessage(String channel, String body) {
        try {
            // channel 格式: bitable:update:{baseId}
            Long baseId = extractBaseIdFromChannel(channel);
            if (baseId == null) {
                log.warn("无法从 channel 提取 baseId: {}", channel);
                return;
            }

            Set<WebSocketSession> sessions = webSocketHandler.getSessions(baseId);
            if (sessions.isEmpty()) {
                return;
            }

            // 直接转发原始 JSON 消息
            TextMessage textMessage = new TextMessage(body);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (Exception e) {
                        log.warn("转发 Redis 消息到 WebSocket 失败: sessionId={}", session.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理 Redis 消息失败: channel={}", channel, e);
        }
    }

    /**
     * 从 channel 名称中提取 baseId
     * channel 格式: bitable:update:{baseId}
     */
    private Long extractBaseIdFromChannel(String channel) {
        String prefix = "bitable:update:";
        if (!channel.startsWith(prefix)) {
            return null;
        }
        try {
            return Long.parseLong(channel.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
