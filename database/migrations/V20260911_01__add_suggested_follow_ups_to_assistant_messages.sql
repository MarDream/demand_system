-- AI助手消息：推荐追问问题（知识库问答解析阶段由 LLM 一并产出）
-- 已部署环境执行：
ALTER TABLE assistant_messages ADD COLUMN suggested_follow_ups JSON DEFAULT NULL COMMENT '推荐追问问题' AFTER warnings;
