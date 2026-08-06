-- 工作流抄送支持：区分可操作审批待办与只读查阅待办。
SET NAMES utf8mb4;

ALTER TABLE requirement_pending_tasks
    ADD COLUMN task_type VARCHAR(32) NOT NULL DEFAULT 'APPROVAL'
        COMMENT '任务类型：APPROVAL=审批待办，CC_READ_ONLY=只读抄送待办'
        AFTER requirement_id;

ALTER TABLE requirement_pending_tasks
    DROP INDEX uk_requirement_position,
    ADD UNIQUE INDEX uk_requirement_position
        (requirement_id, task_type, workflow_instance_id, current_node_id, assignee_type, user_id, role_id, role_group_id, org_id),
    ADD INDEX idx_task_type_user_updated (task_type, user_id, updated_at);
