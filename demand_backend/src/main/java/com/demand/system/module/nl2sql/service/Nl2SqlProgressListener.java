package com.demand.system.module.nl2sql.service;

import com.demand.system.module.assistant.dto.AssistantTask;

/**
 * NL2SQL 执行过程回调：把"意图识别 → 生成 SQL → 安全校验 → 执行查询 → 生成回答"
 * 各阶段的进度以 {@link AssistantTask} 形式推送给前端任务面板。
 */
@FunctionalInterface
public interface Nl2SqlProgressListener {

    /** 任务状态变更（增量推送，前端按 id 合并）。 */
    void onTask(AssistantTask task);

    /** 空实现，便于单测与降级场景。 */
    Nl2SqlProgressListener NOOP = task -> {
    };
}
