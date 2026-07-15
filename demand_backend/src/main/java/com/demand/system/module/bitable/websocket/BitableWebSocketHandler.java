package com.demand.system.module.bitable.websocket;

import com.demand.system.module.bitable.service.BitableCollaborationService;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多维表格 WebSocket Handler
 * <p>
 * 管理 /ws/bitable/{baseId} 的连接，处理 cell_update / cursor_move 消息。
 * session 按 baseId 分组存储，使用 ConcurrentHashMap 保证线程安全。
 */
@Component
public class BitableWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BitableWebSocketHandler.class);

    private final BitableCollaborationService collaborationService;
    private final ObjectMapper objectMapper;

    /**
     * baseId → sessions 映射，线程安全
     */
    private final Map<Long, Set<WebSocketSession>> sessionsByBaseId = new ConcurrentHashMap<>();

    public BitableWebSocketHandler(BitableCollaborationService collaborationService,
                                   ObjectMapper objectMapper) {
        this.collaborationService = collaborationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取 baseId 对应的所有 session（供 Redis 订阅器转发消息使用）
     */
    public Set<WebSocketSession> getSessions(Long baseId) {
        return sessionsByBaseId.getOrDefault(baseId, Set.of());
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        Long baseId = extractBaseId(session);
        if (baseId == null) {
            log.warn("WebSocket 连接缺少 baseId，关闭 session: {}", session.getId());
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (IOException e) {
                log.error("关闭无效 WebSocket session 失败: {}", session.getId(), e);
            }
            return;
        }
        sessionsByBaseId.computeIfAbsent(baseId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket 连接建立: baseId={}, sessionId={}, 当前连接数={}",
                baseId, session.getId(), sessionsByBaseId.get(baseId).size());
    }

    @Override
    public void handleMessage(@NonNull WebSocketSession session,
                              @NonNull WebSocketMessage<?> message) throws Exception {
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
            log.warn("WebSocket 消息解析失败: sessionId={}, payload={}", session.getId(), textMessage.getPayload());
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"消息格式错误，需要 JSON\"}"));
            return;
        }

        String type = node.has("type") ? node.get("type").asText() : "";
        switch (type) {
            case "cell_update" -> handleCellUpdate(session, baseId, node);
            case "cursor_move" -> handleCursorMove(session, baseId, node);
            default -> {
                log.warn("未知 WebSocket 消息类型: type={}, sessionId={}", type, session.getId());
                session.sendMessage(new TextMessage(
                        "{\"type\":\"error\",\"message\":\"未知消息类型: " + type + "\"}"));
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
        log.info("WebSocket 连接关闭: baseId={}, sessionId={}, status={}, 剩余连接数={}",
                baseId, session.getId(), status,
                sessions != null ? sessions.size() : 0);
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

    // ---- 内部消息处理 ----

    private void handleCellUpdate(WebSocketSession session, Long baseId, JsonNode node) {
        Long tableId = node.has("tableId") ? node.get("tableId").asLong() : null;
        Long recordId = node.has("recordId") ? node.get("recordId").asLong() : null;
        Long fieldId = node.has("fieldId") ? node.get("fieldId").asLong() : null;
        Object value = node.has("value") ? objectMapper.convertValue(node.get("value"), Object.class) : null;
        Integer version = node.has("version") ? node.get("version").asInt() : null;
        Long userId = 1L; // TODO: 从 WebSocket session attributes 中获取认证用户

        if (tableId == null || recordId == null || fieldId == null) {
            sendError(session, "cell_update 缺少必填字段 tableId/recordId/fieldId");
            return;
        }

        try {
            collaborationService.handleCellUpdate(baseId, tableId, recordId, fieldId, value, version, userId);
        } catch (Exception e) {
            log.error("处理 cell_update 失败: baseId={}, tableId={}, recordId={}, fieldId={}",
                    baseId, tableId, recordId, fieldId, e);
            sendError(session, "更新失败: " + e.getMessage());
        }
    }

    private void handleCursorMove(WebSocketSession session, Long baseId, JsonNode node) {
        Long tableId = node.has("tableId") ? node.get("tableId").asLong() : null;
        Long recordId = node.has("recordId") ? node.get("recordId").asLong() : null;
        Long fieldId = node.has("fieldId") ? node.get("fieldId").asLong() : null;

        if (tableId == null || recordId == null || fieldId == null) {
            return;
        }

        // 构造广播消息，广播给除发送者外的其他客户端
        try {
            Map<String, Object> cursorMsg = new java.util.LinkedHashMap<>();
            cursorMsg.put("type", "cursor_moved");
            cursorMsg.put("userId", 1L); // TODO: 从 session 获取真实用户
            cursorMsg.put("userName", "用户");
            cursorMsg.put("tableId", tableId);
            cursorMsg.put("recordId", recordId);
            cursorMsg.put("fieldId", fieldId);

            String payload = objectMapper.writeValueAsString(cursorMsg);
            TextMessage textMessage = new TextMessage(payload);

            Set<WebSocketSession> sessions = sessionsByBaseId.get(baseId);
            if (sessions != null) {
                for (WebSocketSession s : sessions) {
                    if (s.isOpen() && !s.getId().equals(session.getId())) {
                        s.sendMessage(textMessage);
                    }
                }
            }
        } catch (Exception e) {
            log.error("广播 cursor_move 失败: baseId={}", baseId, e);
        }
    }

    // ---- 工具方法 ----

    /**
     * 从 WebSocket session URI 中提取 baseId 路径参数
     */
    private Long extractBaseId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        // path 格式: /ws/bitable/{baseId}
        String[] segments = path.split("/");
        if (segments.length >= 3 && "bitable".equals(segments[segments.length - 2])) {
            try {
                return Long.parseLong(segments[segments.length - 1]);
            } catch (NumberFormatException e) {
                log.warn("无效的 baseId: {}", segments[segments.length - 1]);
                return null;
            }
        }
        return null;
    }

    private void sendError(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"" + message + "\"}"));
            }
        } catch (IOException e) {
            log.warn("发送错误消息失败: sessionId={}", session.getId());
        }
    }
}
