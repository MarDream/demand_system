package com.demand.system.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * SSE 流式任务专用线程池。
 * <p>SSE 推流（阻塞写 + LLM 流式读取）若跑在 ForkJoinPool.commonPool 上，
 * 高并发时会饿死公共池并拖累其它并行任务，因此单独隔离；
 * 拒绝时由调用线程执行（退化为同步推流，不丢消息）。</p>
 */
@Configuration
public class SseExecutorConfig {

    public static final String SSE_TASK_EXECUTOR = "sseTaskExecutor";

    @Bean(name = SSE_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("sse-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
