-- 为 assistant_messages 表添加 token 用量统计字段
-- 用于展示单次问答的输入/输出 token 消耗（WorkBuddy 风格结果输出模式）

ALTER TABLE assistant_messages
    ADD COLUMN input_tokens INT NULL COMMENT '输入（提示词）token 数' AFTER citations,
    ADD COLUMN output_tokens INT NULL COMMENT '输出（生成）token 数' AFTER input_tokens,
    ADD COLUMN total_tokens INT NULL COMMENT '总 token 数' AFTER output_tokens;
