-- 按需求类型扩展动态字段，并为历史数据保留兼容默认值
ALTER TABLE custom_fields
    ADD COLUMN field_code VARCHAR(64) NULL COMMENT '稳定字段编码' AFTER project_id,
    ADD COLUMN requirement_type_code VARCHAR(50) NULL COMMENT '需求类型编码' AFTER field_code,
    ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用' AFTER sort_order,
    ADD COLUMN deleted_at DATETIME NULL COMMENT '软删除时间' AFTER enabled;

UPDATE custom_fields
SET field_code = CONCAT('legacy_', id)
WHERE field_code IS NULL;

ALTER TABLE custom_fields
    MODIFY COLUMN field_code VARCHAR(64) NOT NULL,
    ADD UNIQUE INDEX uk_type_field_code (project_id, requirement_type_code, field_code),
    ADD INDEX idx_type_enabled (project_id, requirement_type_code, enabled, deleted_at);

ALTER TABLE requirement_custom_field_values
    ADD COLUMN value_boolean TINYINT NULL COMMENT '布尔值' AFTER value_date,
    ADD COLUMN value_user_id BIGINT NULL COMMENT '单用户值' AFTER value_boolean,
    ADD COLUMN field_code_snapshot VARCHAR(64) NULL COMMENT '字段编码快照' AFTER field_id,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS requirement_custom_field_multi_values (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    requirement_id BIGINT UNSIGNED NOT NULL,
    field_id BIGINT UNSIGNED NOT NULL,
    value_type VARCHAR(20) NOT NULL COMMENT 'OPTION/USER/FILE',
    value_text VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_requirement_field_multi (requirement_id, field_id, value_type, value_text),
    KEY idx_field_multi_value (field_id, value_type, value_text),
    KEY idx_requirement_field_multi (requirement_id, field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='需求动态字段多值明细';
