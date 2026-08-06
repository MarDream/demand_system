-- 为 assistant_messages 表添加任务列表（tasks）字段
-- 用于存储检索过程中的任务列表，支持历史会话回放
ALTER TABLE assistant_messages
    ADD COLUMN tasks JSON DEFAULT NULL COMMENT '任务列表（检索过程）' AFTER thinking_steps;
