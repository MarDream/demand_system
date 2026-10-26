package com.demand.system.module.assistant.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 智能体子 Agent 专用线程池。
 * <p>子 Agent 各自持有独立的工具循环，并行数有限（≤4），
 * 与 SSE 推流线程池隔离，避免互相占用；拒绝时由调用线程执行（退化为串行，不丢任务）。</p>
 */
@Configuration
public class AgentExecutorConfig {

    public static final String AGENT_TASK_EXECUTOR = "agentTaskExecutor";

    @Bean(name = AGENT_TASK_EXECUTOR)
    public Executor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("agent-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
