-- Persist assistant retrieval capability/degradation warnings.
ALTER TABLE assistant_messages
    ADD COLUMN warnings JSON DEFAULT NULL COMMENT '检索降级与能力提示' AFTER citations;
