package com.demand.system.module.assistant.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 检索任务节点，对标 WorkBuddy 任务列表。
 * <p>每个任务代表检索流程中的一个步骤（如向量检索、关键词检索、Reranker、LLM）。
 * SSE 通过 {@code taskUpdate} 事件增量推送每个任务的状态变更。
 */
public class AssistantTask {

    /** 任务唯一 ID（在同一消息内唯一） */
    private String id;
    /** 任务标题 */
    private String title;
    /** 任务状态：pending → running → completed / failed */
    private String status;
    /** 任务开始时间（epoch millis） */
    private Long startedAt;
    /** 任务完成时间（epoch millis） */
    private Long completedAt;
    /** 任务过程日志行（增量追加） */
    private List<TaskLog> logs;

    public AssistantTask() {}

    public AssistantTask(String id, String title) {
        this.id = id;
        this.title = title;
        this.status = "pending";
        this.logs = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getStartedAt() { return startedAt; }
    public void setStartedAt(Long startedAt) { this.startedAt = startedAt; }

    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }

    public List<TaskLog> getLogs() { return logs; }
    public void setLogs(List<TaskLog> logs) { this.logs = logs; }

    /**
     * 标记任务开始运行，追加日志。
     */
    public void start(String message) {
        this.status = "running";
        this.startedAt = Instant.now().toEpochMilli();
        if (message != null && !message.isBlank()) {
            log("info", message);
        }
    }

    /**
     * 标记任务成功完成。
     */
    public void complete(String message) {
        this.status = "completed";
        this.completedAt = Instant.now().toEpochMilli();
        if (message != null && !message.isBlank()) {
            log("info", message);
        }
    }

    /**
     * 标记任务失败。
     */
    public void fail(String message) {
        this.status = "failed";
        this.completedAt = Instant.now().toEpochMilli();
        log("error", message != null ? message : "任务失败");
    }

    /**
     * 追加一条过程日志。
     */
    public void log(String level, String message) {
        if (message == null || message.isBlank()) return;
        this.logs.add(new TaskLog(Instant.now().toEpochMilli(), level, message));
    }

    // ===== 内部类 =====

    public static class TaskLog {
        private Long timestamp;
        private String level;
        private String message;

        public TaskLog() {}

        public TaskLog(Long timestamp, String level, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }

        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
