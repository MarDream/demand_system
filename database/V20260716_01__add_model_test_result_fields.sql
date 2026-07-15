ALTER TABLE `llm_models`
    ADD COLUMN `test_content` MEDIUMTEXT DEFAULT NULL COMMENT '最近测试响应内容（完整响应文本）' AFTER `test_at`,
    ADD COLUMN `test_prompt_tokens` INT DEFAULT NULL COMMENT '最近测试请求 Token 数' AFTER `test_content`,
    ADD COLUMN `test_completion_tokens` INT DEFAULT NULL COMMENT '最近测试响应 Token 数' AFTER `test_prompt_tokens`,
    ADD COLUMN `test_total_tokens` INT DEFAULT NULL COMMENT '最近测试总 Token 数' AFTER `test_completion_tokens`,
    ADD COLUMN `test_response_model` VARCHAR(200) DEFAULT NULL COMMENT '最近测试实际响应的模型名' AFTER `test_total_tokens`;
