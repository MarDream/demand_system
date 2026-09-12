package com.demand.system.module.bitable.config;

import com.demand.system.common.util.JwtUtils;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 多维表格 WebSocket 握手鉴权，并将真实用户信息写入 session attributes。
 */
@Component
public class BitableWebSocketAuthInterceptor implements HandshakeInterceptor {

    private final UserMapper userMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public BitableWebSocketAuthInterceptor(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        // 优先从 Sec-WebSocket-Protocol 头取 token（不进入 URL 与访问日志），
        // 兼容旧的 accessToken query 参数（逐步废弃）
        String token = extractTokenFromSubProtocol(request);
        if (!StringUtils.hasText(token)) {
            token = UriComponentsBuilder.fromUri(request.getURI())
                    .build()
                    .getQueryParams()
                    .getFirst("accessToken");
        }
        if (!StringUtils.hasText(token) || !JwtUtils.isTokenValid(token, jwtSecret)) {
            return false;
        }

        Long userId = JwtUtils.getUserId(token, jwtSecret);
        User user = userMapper.selectById(userId);
        if (user == null || !User.STATUS_ACTIVE.equals(user.getStatus())) {
            return false;
        }

        String displayName = StringUtils.hasText(user.getRealName())
                ? user.getRealName().trim()
                : user.getUsername();
        attributes.put("userId", user.getId());
        attributes.put("userName", displayName);
        attributes.put("avatar", user.getAvatar());
        return true;
    }

    /**
     * 浏览器 WebSocket 无法自定义请求头，约定把 token 作为 Sec-WebSocket-Protocol 的
     * 子协议值传入（"bearer,<token>"）。
     */
    private String extractTokenFromSubProtocol(ServerHttpRequest request) {
        String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (!StringUtils.hasText(protocols)) {
            return null;
        }
        String[] parts = protocols.split(",");
        if (parts.length >= 2 && "bearer".equalsIgnoreCase(parts[0].trim())) {
            return parts[1].trim();
        }
        return null;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }
}
