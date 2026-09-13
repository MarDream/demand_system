-- =====================================================
-- LLM 接入组增加官网地址字段（可选，用于快速跳转供应商官网/控制台）
-- =====================================================

ALTER TABLE `llm_providers`
    ADD COLUMN `website_url` VARCHAR(500) NULL DEFAULT NULL COMMENT '官网地址（可选）' AFTER `base_url`;
