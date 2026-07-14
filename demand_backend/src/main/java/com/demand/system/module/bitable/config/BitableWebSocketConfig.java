package com.demand.system.module.bitable.config;

import com.demand.system.module.bitable.websocket.BitableWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 多维表格 WebSocket 配置
 * <p>
 * 注册 Handler 到 /ws/bitable/{baseId}，允许前端 5170 端口跨域握手。
 */
@Configuration
@EnableWebSocket
public class BitableWebSocketConfig implements WebSocketConfigurer {

    private final BitableWebSocketHandler bitableWebSocketHandler;

    public BitableWebSocketConfig(BitableWebSocketHandler bitableWebSocketHandler) {
        this.bitableWebSocketHandler = bitableWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(bitableWebSocketHandler, "/ws/bitable/{baseId}")
                .setAllowedOrigins("http://localhost:5170", "http://127.0.0.1:5170");
    }
}
