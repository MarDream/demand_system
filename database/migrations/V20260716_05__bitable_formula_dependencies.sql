-- 公式依赖图
CREATE TABLE IF NOT EXISTS `bitable_formula_dependencies` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `formula_field_id` BIGINT UNSIGNED NOT NULL COMMENT '公式字段ID',
  `dependency_field_id` BIGINT UNSIGNED NOT NULL COMMENT '依赖的字段ID',
  `dependency_kind` VARCHAR(20) NOT NULL DEFAULT 'direct' COMMENT '依赖类型: direct/lookup/rollup',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_formula_dep` (`formula_field_id`, `dependency_field_id`),
  INDEX `idx_dependency_field_id` (`dependency_field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-公式依赖关系';
