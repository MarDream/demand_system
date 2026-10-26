package com.demand.system.module.assistant.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 写操作工具的用户确认注册表。
 * <p>执行循环遇到需确认的工具时注册挂起请求并通过 SSE 下发 permissionRequest，
 * 前端调 tool-confirm 端点回填决议；超时按拒绝处理；
 * SSE 断开（用户停止/关页）时由编排器调用 {@link #denyAllForSession} 全部拒绝。</p>
 */
@Component
public class AgentConfirmationRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentConfirmationRegistry.class);

    /** 确认等待上限：超时按拒绝处理，循环继续 */
    public static final long CONFIRM_TIMEOUT_MS = 120_000L;

    private static class PendingConfirmation {
        final Long userId;
        final Long sessionId;
        final CompletableFuture<Boolean> future = new CompletableFuture<>();

        PendingConfirmation(Long userId, Long sessionId) {
            this.userId = userId;
            this.sessionId = sessionId;
        }
    }

    private final Map<String, PendingConfirmation> pending = new ConcurrentHashMap<>();

    /** 注册一条待确认请求，返回前端回填时使用的 requestId */
    public String register(String requestId, Long userId, Long sessionId) {
        PendingConfirmation confirmation = new PendingConfirmation(userId, sessionId);
        pending.put(requestId, confirmation);
        return requestId;
    }

    /**
     * 阻塞等待用户决议。
     *
     * @return true=允许执行；false=拒绝或超时
     */
    public boolean await(String requestId, long timeoutMs) {
        PendingConfirmation confirmation = pending.get(requestId);
        if (confirmation == null) {
            return false;
        }
        try {
            Boolean approved = confirmation.future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(approved);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            // 超时或被取消：按拒绝处理
            return false;
        } finally {
            pending.remove(requestId);
        }
    }

    /** 前端回填决议；仅会话归属用户可操作，返回是否命中有效请求 */
    public boolean resolve(String requestId, Long userId, boolean approved) {
        PendingConfirmation confirmation = pending.get(requestId);
        if (confirmation == null) {
            return false;
        }
        if (!confirmation.userId.equals(userId)) {
            log.warn("tool confirm user mismatch, requestId={}, ownerId={}, actorId={}",
                    requestId, confirmation.userId, userId);
            return false;
        }
        pending.remove(requestId);
        confirmation.future.complete(approved);
        return true;
    }

    /** SSE 中断时按会话维度拒绝全部挂起确认（循环侧 await 返回 false 后继续兜底流程） */
    public void denyAllForSession(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        for (Map.Entry<String, PendingConfirmation> entry : pending.entrySet()) {
            if (sessionId.equals(entry.getValue().sessionId)) {
                pending.remove(entry.getKey());
                entry.getValue().future.complete(false);
                log.info("denied pending tool confirmation on disconnect, requestId={}", entry.getKey());
            }
        }
    }

    public int pendingCount() {
        return pending.size();
    }
}
