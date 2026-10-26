package com.demand.system.module.assistant.agent.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 写操作确认注册表：授权回填、非本人拒绝、超时按拒绝、按会话批量拒绝 */
class AgentConfirmationRegistryTest {

    private final AgentConfirmationRegistry registry = new AgentConfirmationRegistry();

    @Test
    void resolveByOwnerSucceeds() throws Exception {
        registry.register("req-1", 7L, 100L);
        Thread waiter = new Thread(() -> {
            boolean approved = registry.await("req-1", 5000);
            assertTrue(approved, "owner approve should pass through");
        });
        waiter.start();
        Thread.sleep(50);
        assertTrue(registry.resolve("req-1", 7L, true));
        waiter.join(2000);
    }

    @Test
    void resolveByOtherUserRejected() throws Exception {
        registry.register("req-2", 7L, 100L);
        Thread waiter = new Thread(() -> {
            boolean approved = registry.await("req-2", 1000);
            assertFalse(approved, "other user cannot approve; timeout counts as deny");
        });
        waiter.start();
        Thread.sleep(50);
        assertFalse(registry.resolve("req-2", 8L, true));
        waiter.join(2000);
    }

    @Test
    void timeoutCountsAsDeny() throws Exception {
        registry.register("req-3", 7L, 100L);
        boolean approved = registry.await("req-3", 100);
        assertFalse(approved);
    }

    @Test
    void denyAllForSessionResolvesPending() throws Exception {
        registry.register("req-4", 7L, 100L);
        registry.register("req-5", 7L, 100L);
        registry.register("req-6", 9L, 200L);
        Thread waiter = new Thread(() -> assertFalse(registry.await("req-4", 5000)));
        waiter.start();
        Thread.sleep(50);
        registry.denyAllForSession(100L);
        waiter.join(2000);
        // 其它会话的挂起不受影响，可正常回填
        assertTrue(registry.resolve("req-6", 9L, true));
        assertFalse(registry.resolve("req-5", 7L, true), "已随会话拒绝的请求不可再回填");
    }
}
