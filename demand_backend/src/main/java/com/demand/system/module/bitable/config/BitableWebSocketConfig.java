package com.demand.system.module.bitable.config;

import com.demand.system.module.bitable.websocket.BitableWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Collections;
import java.util.List;

/**
 * 多维表格 WebSocket 配置
 * <p>
 * 注册 Handler 到 /ws/bitable/{baseId}，允许前端 5170 端口跨域握手。
 * <p>
 * Handler 声明支持 "bearer" 子协议：前端把 JWT 作为 Sec-WebSocket-Protocol 的
 * 第二个子协议传入（["bearer", <token>]），Spring 握手时回显 "bearer"，
 * 浏览器据此确认子协议协商成功，token 不再出现在 URL 中。
 */
@Configuration
@EnableWebSocket
public class BitableWebSocketConfig implements WebSocketConfigurer {

    /** 委托实际处理并声明支持的子协议，供握手回显 */
    public static class SubProtocolAwareHandler implements WebSocketHandler, SubProtocolCapable {

        private final BitableWebSocketHandler delegate;

        public SubProtocolAwareHandler(BitableWebSocketHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<String> getSubProtocols() {
            return Collections.singletonList("bearer");
        }

        @Override
        public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession session) throws Exception {
            delegate.afterConnectionEstablished(session);
        }

        @Override
        public void handleMessage(org.springframework.web.socket.WebSocketSession session,
                                  org.springframework.web.socket.WebSocketMessage<?> message) throws Exception {
            delegate.handleMessage(session, message);
        }

        @Override
        public void handleTransportError(org.springframework.web.socket.WebSocketSession session,
                                         Throwable exception) throws Exception {
            delegate.handleTransportError(session, exception);
        }

        @Override
        public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session,
                                          org.springframework.web.socket.CloseStatus closeStatus) throws Exception {
            delegate.afterConnectionClosed(session, closeStatus);
        }

        @Override
        public boolean supportsPartialMessages() {
            return delegate.supportsPartialMessages();
        }
    }

    private final BitableWebSocketHandler bitableWebSocketHandler;
    private final BitableWebSocketAuthInterceptor authInterceptor;

    public BitableWebSocketConfig(BitableWebSocketHandler bitableWebSocketHandler,
                                  BitableWebSocketAuthInterceptor authInterceptor) {
        this.bitableWebSocketHandler = bitableWebSocketHandler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new SubProtocolAwareHandler(bitableWebSocketHandler), "/ws/bitable/{baseId}")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("http://localhost:5170", "http://127.0.0.1:5170");
    }
}
