-- 为 assistant_messages 表添加 RAG 相关字段
-- 用于存储知识库检索的思维链、摘要、召回数量和引用文档

ALTER TABLE assistant_messages
    ADD COLUMN thinking_steps JSON COMMENT '思维链步骤（RAG 检索过程）' AFTER sources,
    ADD COLUMN process_summary TEXT COMMENT '检索过程摘要' AFTER thinking_steps,
    ADD COLUMN retrieved_count INT COMMENT '命中的片段数量' AFTER process_summary,
    ADD COLUMN citations JSON COMMENT '引用文档列表' AFTER retrieved_count;
