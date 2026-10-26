package com.demand.system.module.bitable.config;

import com.demand.system.module.bitable.websocket.BitableWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 多维表格 WebSocket 配置
 * <p>
 * 注册 Handler 到 /ws/bitable/{baseId}。
 * <p>
 * ⚠️ 允许的握手 Origin 与 HTTP CORS 共用同一份配置（{@code cors.allowed-origins}，
 * 线上由环境变量 {@code CORS_ALLOWED_ORIGINS} 注入）。
 * <p>
 * 历史教训：这里原本写死
 * {@code setAllowedOrigins("http://localhost:5170", "http://127.0.0.1:5170")}，
 * 于是所有真实入口（生产域名 / 公网 IP / 内网域名等）在握手阶段全部被
 * {@code OriginHandshakeInterceptor} 判为非法 → <b>403（响应体为空）</b>，
 * 而普通 HTTP 接口因为 CORS 配的是另一份白名单，反而是好的 —— 极易误判成"WebSocket 服务端有问题"。
 * <p>
 * 判据：<b>只要「WS 白名单」和「CORS 白名单」是两份配置，就一定会分叉。</b>
 * 所以这里不再自己维护列表，直接复用 {@code cors.allowed-origins}。
 * 若将来要放开新域名，<b>只改一处</b>（{@code CORS_ALLOWED_ORIGINS}），两个门同时生效。
 * <p>
 * Handler 声明支持 "bearer" 子协议：前端把 JWT 作为 Sec-WebSocket-Protocol 的
 * 第二个子协议传入（["bearer", &lt;token&gt;]），Spring 握手时回显 "bearer"，
 * 浏览器据此确认子协议协商成功，token 不再出现在 URL 中。
 */
@Configuration
@EnableWebSocket
public class BitableWebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(BitableWebSocketConfig.class);

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

    /**
     * 允许的握手 Origin，多个用逗号分隔。
     * <p>
     * ⚠️ 与 {@code CorsConfig} 读的是<b>同一个属性</b>（{@code cors.allowed-origins}）——
     * 这是刻意的：两份白名单必然分叉，见类注释里的历史教训。改这里等于改 CORS，反之亦然。
     */
    @Value("${cors.allowed-origins:http://localhost:5170,http://127.0.0.1:5170}")
    private String allowedOrigins;

    public BitableWebSocketConfig(BitableWebSocketHandler bitableWebSocketHandler,
                                  BitableWebSocketAuthInterceptor authInterceptor) {
        this.bitableWebSocketHandler = bitableWebSocketHandler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        // 打出来，避免"配了但没生效"要靠线上复现才发现
        log.info("WebSocket /ws/bitable/{{baseId}} 允许的握手 Origin（与 cors.allowed-origins 同源，共 {} 个）: {}",
                origins.length, Arrays.toString(origins));

        registry.addHandler(new SubProtocolAwareHandler(bitableWebSocketHandler), "/ws/bitable/{baseId}")
                .addInterceptors(authInterceptor)
                // 用 patterns 而非 origins：既能精确匹配，也能支持将来配置成通配（如 https://*.example.com）
                .setAllowedOriginPatterns(origins);
    }
}
