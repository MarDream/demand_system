package com.demand.system.module.bitable.config;

import com.demand.system.common.utils.JwtUtils;
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
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("accessToken");
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

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }
}
