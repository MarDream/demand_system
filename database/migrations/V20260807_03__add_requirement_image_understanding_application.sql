-- 工单正文图片理解统一通过“模型配置 - 模型应用”配置。
INSERT INTO `llm_applications`
    (`code`, `name`, `description`, `model_type`, `model_id`, `enabled`, `sort_order`)
VALUES
    ('knowledge.image-understanding', '工单正文图片理解',
     '识别工单正文图片中的文字、页面、图表、错误信息和关键数值',
     'vision', NULL, 0, 65)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `description` = VALUES(`description`),
    `model_type` = VALUES(`model_type`),
    `sort_order` = VALUES(`sort_order`);
