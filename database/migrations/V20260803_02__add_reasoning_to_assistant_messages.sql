-- 为 assistant_messages 表添加深度思考（reasoning）字段
-- 用于存储 LLM 深度思考内容（WorkBuddy 同款「已深度思考」展示）

ALTER TABLE assistant_messages
    ADD COLUMN reasoning LONGTEXT NULL COMMENT '深度思考内容（LLM reasoning）' AFTER citations;
