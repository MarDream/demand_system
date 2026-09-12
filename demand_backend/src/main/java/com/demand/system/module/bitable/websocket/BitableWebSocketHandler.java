package com.demand.system.module.bitable.websocket;

import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多维表格 WebSocket Handler。
 * 管理在线协作者，并处理 cursor_move 消息。
 * 单元格数据写入一律走 REST（BitableRecordService.updateCell），
 * WS 不提供数据写入上行，避免绕过权限与伪造审计日志。
 */
@Component
public class BitableWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BitableWebSocketHandler.class);

    private final BitableAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessionsByBaseId = new ConcurrentHashMap<>();

    public BitableWebSocketHandler(BitableAuthorizationService authorizationService,
                                   ObjectMapper objectMapper) {
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取 baseId 对应的所有 session（供 Redis 订阅器转发消息使用）。
     */
    public Set<WebSocketSession> getSessions(Long baseId) {
        return sessionsByBaseId.getOrDefault(baseId, Set.of());
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        Long baseId = extractBaseId(session);
        Long userId = sessionAttribute(session, "userId", Long.class);
        if (baseId == null || userId == null) {
            log.warn("WebSocket 连接缺少 baseId 或认证用户，关闭 session: {}", session.getId());
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        // 校验用户是该 Base 的成员，非成员拒绝建立协作连接
        if (authorizationService.getMemberRole(baseId, userId) == null) {
            log.warn("非 Base 成员尝试建立 WebSocket 连接: baseId={}, userId={}, sessionId={}",
                    baseId, userId, session.getId());
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessionsByBaseId.computeIfAbsent(baseId, key -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket 连接建立: baseId={}, userId={}, sessionId={}, 当前连接数={}",
                baseId, userId, session.getId(), sessionsByBaseId.get(baseId).size());
        broadcastPresence(baseId);
    }

    @Override
    public void handleMessage(@NonNull WebSocketSession session,
                              @NonNull WebSocketMessage<?> message) {
        if (!(message instanceof TextMessage textMessage)) {
            return;
        }

        Long baseId = extractBaseId(session);
        if (baseId == null) {
            return;
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(textMessage.getPayload());
        } catch (Exception e) {
            log.warn("WebSocket 消息解析失败: sessionId={}", session.getId());
            sendError(session, "消息格式错误，需要 JSON");
            return;
        }

        String type = node.has("type") ? node.get("type").asText() : "";
        switch (type) {
            case "cell_update" -> // 单元格写入已统一由 REST 路径处理，WS 上行不再受理，防止绕过权限和伪造操作日志
                    sendError(session, "单元格更新请通过 REST 接口提交，WebSocket 不接受 cell_update");
            case "cursor_move" -> handleCursorMove(session, baseId, node);
            default -> {
                log.warn("未知 WebSocket 消息类型: type={}, sessionId={}", type, session.getId());
                sendError(session, "未知消息类型: " + type);
            }
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session,
                                      @NonNull CloseStatus status) {
        Long baseId = extractBaseId(session);
        if (baseId == null) {
            return;
        }

        Set<WebSocketSession> sessions = sessionsByBaseId.get(baseId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByBaseId.remove(baseId);
            }
        }
        log.info("WebSocket 连接关闭: baseId={}, userId={}, sessionId={}, status={}, 剩余连接数={}",
                baseId, sessionAttribute(session, "userId", Long.class), session.getId(), status,
                sessions != null ? sessions.size() : 0);
        broadcastPresence(baseId);
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session,
                                     @NonNull Throwable exception) {
        log.error("WebSocket 传输错误: sessionId={}", session.getId(), exception);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void handleCursorMove(WebSocketSession session, Long baseId, JsonNode node) {
        Long tableId = node.has("tableId") ? node.get("tableId").asLong() : null;
        Long recordId = node.has("recordId") ? node.get("recordId").asLong() : null;
        Long fieldId = node.has("fieldId") ? node.get("fieldId").asLong() : null;
        Long userId = sessionAttribute(session, "userId", Long.class);
        String userName = sessionAttribute(session, "userName", String.class);

        if (tableId == null || recordId == null || fieldId == null || userId == null) {
            return;
        }

        Map<String, Object> cursorMessage = new LinkedHashMap<>();
        cursorMessage.put("type", "cursor_moved");
        cursorMessage.put("userId", userId);
        cursorMessage.put("userName", userName);
        cursorMessage.put("tableId", tableId);
        cursorMessage.put("recordId", recordId);
        cursorMessage.put("fieldId", fieldId);
        broadcastExcept(baseId, session.getId(), cursorMessage);
    }

    /**
     * 将当前 base 下真实在线用户广播给所有客户端。同一用户打开多个标签页只显示一次。
     */
    private void broadcastPresence(Long baseId) {
        Set<WebSocketSession> sessions = sessionsByBaseId.get(baseId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        Map<Long, Map<String, Object>> uniqueUsers = new LinkedHashMap<>();
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            Long userId = sessionAttribute(session, "userId", Long.class);
            if (userId == null || uniqueUsers.containsKey(userId)) {
                continue;
            }
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", userId);
            user.put("name", sessionAttribute(session, "userName", String.class));
            user.put("avatar", sessionAttribute(session, "avatar", String.class));
            uniqueUsers.put(userId, user);
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "presence_updated");
        message.put("users", uniqueUsers.values());
        broadcast(baseId, message);
    }

    private void broadcast(Long baseId, Object message) {
        broadcastExcept(baseId, null, message);
    }

    private void broadcastExcept(Long baseId, String excludedSessionId, Object message) {
        Set<WebSocketSession> sessions = sessionsByBaseId.get(baseId);
        if (sessions == null) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("序列化 WebSocket 消息失败: baseId={}", baseId, e);
            return;
        }
        // 逐个 session 独立捕获异常，避免单个客户端发送失败中断整体广播
        for (WebSocketSession session : sessions) {
            if (session.isOpen() && !session.getId().equals(excludedSessionId)) {
                try {
                    sendText(session, payload);
                } catch (Exception e) {
                    log.warn("发送 WebSocket 消息失败: sessionId={}", session.getId(), e);
                }
            }
        }
    }

    private Long extractBaseId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        String[] segments = uri.getPath().split("/");
        if (segments.length >= 3 && "bitable".equals(segments[segments.length - 2])) {
            try {
                return Long.parseLong(segments[segments.length - 1]);
            } catch (NumberFormatException e) {
                log.warn("无效的 baseId: {}", segments[segments.length - 1]);
            }
        }
        return null;
    }

    private <T> T sessionAttribute(WebSocketSession session, String key, Class<T> type) {
        Object value = session.getAttributes().get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    private void sendError(WebSocketSession session, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("message", message);
        try {
            sendText(session, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("发送错误消息失败: sessionId={}", session.getId(), e);
        }
    }

    private void sendText(WebSocketSession session, String payload) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.warn("关闭 WebSocket session 失败: {}", session.getId(), e);
        }
    }
}
